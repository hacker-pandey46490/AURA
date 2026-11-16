package com.aura.core.model

import com.aura.core.api.ActionExecutor
import com.aura.core.api.ScreenObserver

/**
 * Runs steps one by one: execute, wait for the UI to settle, verify the expectation.
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
    suspend fun run(steps: List<TaskStep>, onUpdate: (StepUpdate) -> Unit): TaskOutcome {
        for ((index, step) in steps.withIndex()) {
            val before = observer.capture()
            val total = step.maxRetries + 1
            var executed = false
            var succeeded = false
            var detail = ""
            var attemptsUsed = 0

            for (attempt in 1..total) {
                attemptsUsed = attempt
                onUpdate(StepUpdate(index, StepState.RUNNING, if (attempt > 1) "Retry ${attempt - 1}" else "Running", attempt))

                if (!executed) {
                    val result = executor.execute(step.action)
                    if (result.success) {
                        executed = true
                        detail = result.message
                    } else {
                        detail = result.message
                    }
                }
                if (executed) {
                    pause(settleMs)
                    val problem = verify(step.expect, before, observer.capture())
                    if (problem == null) {
                        succeeded = true
                        break
                    }
                    detail = problem
                }
                if (attempt < total) pause(retryDelayMs)
            }

            if (succeeded) {
                onUpdate(StepUpdate(index, StepState.SUCCEEDED, detail, attemptsUsed))
            } else {
                onUpdate(StepUpdate(index, StepState.FAILED, detail, attemptsUsed))
                return TaskOutcome.Failed(index, detail)
            }
        }
        return TaskOutcome.Completed
    }

    private fun verify(expect: Expectation, before: ScreenSnapshot?, after: ScreenSnapshot?): String? = when (expect) {
        Expectation.None -> null
        Expectation.ScreenChanged -> when {
            after == null -> "Screen not readable"
            before == null || after.packageName != before.packageName || after.root != before.root -> null
            else -> "Screen did not change"
        }
        is Expectation.TextVisible ->
            if (after?.containsText(expect.text) == true) null else "Text \"${expect.text}\" not found"
        is Expectation.PackageIs ->
            if (after?.packageName == expect.packageName) null
            else "Expected app ${expect.packageName} but saw ${after?.packageName ?: "nothing"}"
    }
}
