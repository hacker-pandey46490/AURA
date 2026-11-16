package com.aura.core.model

/** Flags steps that may send, pay, delete or otherwise change things, so the user can approve them first. */
object SensitivityGuard {
    private val keywords = listOf(
        "send", "pay", "purchase", "buy", "order", "delete", "remove", "transfer",
        "submit", "uninstall", "erase", "reset", "sign out", "log out", "post", "confirm",
    )

    fun reason(step: TaskStep): String? {
        val target = when (val a = step.action) {
            is Action.ClickByText -> a.text
            is Action.Click -> a.selector.describe()
            is Action.LongClick -> a.selector.describe()
            is Action.DoubleTap -> a.selector.describe()
            is Action.TypeText -> a.selector?.describe()
            else -> null
        } ?: return null
        val hit = keywords.firstOrNull { target.contains(it, ignoreCase = true) } ?: return null
        return "targets something matching \"$hit\""
    }

    fun sensitiveSteps(steps: List<TaskStep>): List<Pair<Int, String>> =
        steps.mapIndexedNotNull { i, s -> reason(s)?.let { i to it } }
}
