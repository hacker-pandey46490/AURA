package com.aura.core.model

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/** Primitive actions the executor understands. */
sealed interface Action {
    data object Back : Action
    data object Home : Action
    data object ScrollForward : Action
    data object ScrollBackward : Action

    /** Presses the keyboard's Enter / Search key in the focused input (Android 11+). */
    data object Enter : Action
    data class ClickByText(val text: String) : Action
    data class Click(val selector: Selector) : Action
    data class LongClick(val selector: Selector) : Action
    data class DoubleTap(val selector: Selector) : Action

    /** Types [text] into the element matching [selector], or into the focused input when null. */
    data class TypeText(val selector: Selector?, val text: String) : Action

    /** Direction is the direction the finger moves. */
    data class Swipe(val direction: SwipeDirection) : Action
    data class LaunchApp(val appName: String) : Action
    data class Wait(val millis: Long) : Action
}

data class ActionResult(val success: Boolean, val message: String)

enum class AgentStatus { IDLE, RUNNING, COMPLETED, STOPPED, FAILED }

/** Human-readable description. Typed text is never included (it may be sensitive). */
fun Action.describe(): String = when (this) {
    Action.Back -> "back"
    Action.Home -> "home"
    Action.ScrollForward -> "scroll forward"
    Action.ScrollBackward -> "scroll backward"
    Action.Enter -> "press enter"
    is Action.ClickByText -> "click text \"$text\""
    is Action.Click -> "click ${selector.describe()}"
    is Action.LongClick -> "long-click ${selector.describe()}"
    is Action.DoubleTap -> "double-tap ${selector.describe()}"
    is Action.TypeText -> "type ${text.length} chars into " + (selector?.describe() ?: "focused field")
    is Action.Swipe -> "swipe ${direction.name.lowercase()}"
    is Action.LaunchApp -> "launch \"$appName\""
    is Action.Wait -> "wait $millis ms"
}
