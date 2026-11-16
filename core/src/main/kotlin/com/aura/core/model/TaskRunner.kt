package com.aura.core.model

import com.aura.core.api.ActionExecutor
import com.aura.core.api.ScreenObserver

data class StepOutcome(val success: Boolean, val detail: String, val attempts: Int)

/**
 * Runs steps: execute, wait for the UI to settle, verify the expectation.
 *
 * Retry policy: if the action itself fails it is executed again. If the action succeeded but the
 * expectation is not met yet, only the verification is repeated (the action is NOT re-executed),
 * so a click is never performed twice by accident.
 */
class TaskRunner(
    private val observer: ScreenObserver,
    private val executor: ActionExecutor,
    private val pause: suspend (Long) -> Unit,
    private val settleMs: Long = 700,
    private val retryDelayMs: Long = 500,
) {
    /** Runs one step with retries and verification. [onAttempt] is called before every attempt. */
    suspend fun runStep(
        step: TaskStep,
        onAttempt: (attempt: Int, detail: String) -> Unit = { _, _ -> },
    ): StepOutcome {
        val before = observer.capture()
        val total = step.maxRetries + 1
        var executed = false
        var succeeded = false
        var detail = ""
        var attemptsUsed = 0

        for (attempt in 1..total) {
            attemptsUsed = attempt
            onAttempt(attempt, if (attempt > 1) "Retry ${attempt - 1}" else "Running")

            if (!executed) {
                val result = executor.execute(step.action)
                detail = result.message
                if (result.success) executed = true
            }
            if (executed) {
                pause(settleMs)
                val problem = checkExpectation(step.expect, before, observer.capture())
                if (problem == null) {
                    succeeded = true
                    break
                }
                detail = problem
            }
            if (attempt < total) pause(retryDelayMs)
        }
        return StepOutcome(succeeded, detail, attemptsUsed)
    }

    suspend fun run(steps: List<TaskStep>, onUpdate: (StepUpdate) -> Unit): TaskOutcome {
        for ((index, step) in steps.withIndex()) {
            val outcome = runStep(step) { attempt, detail ->
                onUpdate(StepUpdate(index, StepState.RUNNING, detail, attempt))
            }
            if (outcome.success) {
                onUpdate(StepUpdate(index, StepState.SUCCEEDED, outcome.detail, outcome.attempts))
            } else {
                onUpdate(StepUpdate(index, StepState.FAILED, outcome.detail, outcome.attempts))
                return TaskOutcome.Failed(index, outcome.detail)
            }
        }
        return TaskOutcome.Completed
    }
}
