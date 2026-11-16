package com.aura.core.model

/** What should be true on screen after a step. Checked by the runner. */
sealed interface Expectation {
    data object None : Expectation
    data object ScreenChanged : Expectation
    data class TextVisible(val text: String) : Expectation
    data class PackageIs(val packageName: String) : Expectation
}

fun Expectation.describe(): String = when (this) {
    Expectation.None -> "no check"
    Expectation.ScreenChanged -> "screen changes"
    is Expectation.TextVisible -> "text \"$text\" visible"
    is Expectation.PackageIs -> "app is $packageName"
}

data class TaskStep(
    val action: Action,
    val expect: Expectation = Expectation.None,
    val maxRetries: Int = 2,
) {
    val label: String
        get() = action.describe() + if (expect == Expectation.None) "" else "  => expect ${expect.describe()}"
}

/** PENDING grey, RUNNING blue, SUCCEEDED green, WAITING yellow (paused / waiting for the user), FAILED red, SKIPPED light grey. */
enum class StepState { PENDING, RUNNING, SUCCEEDED, FAILED, SKIPPED, WAITING }

data class StepUpdate(val index: Int, val state: StepState, val detail: String, val attempts: Int)

data class StepProgress(val label: String, val state: StepState, val detail: String, val attempts: Int)

sealed interface TaskOutcome {
    data object Completed : TaskOutcome
    data class Failed(val stepIndex: Int, val reason: String) : TaskOutcome
}
