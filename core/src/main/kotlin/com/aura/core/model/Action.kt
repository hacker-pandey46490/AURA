package com.aura.core.model

/** Primitive actions the executor understands. Phase 1 keeps this deliberately small. */
sealed interface Action {
    data object Back : Action
    data object Home : Action
    data object ScrollForward : Action
    data class ClickByText(val text: String) : Action
}

data class ActionResult(val success: Boolean, val message: String)

enum class AgentStatus { IDLE, RUNNING, COMPLETED, STOPPED, FAILED }
