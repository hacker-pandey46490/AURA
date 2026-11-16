package com.aura.core.model

fun UiNode.flatten(): Sequence<UiNode> = sequence {
    yield(this@flatten)
    for (child in children) yieldAll(child.flatten())
}

/** True if any node's text or description contains [query]. */
fun ScreenSnapshot.containsText(query: String, ignoreCase: Boolean = true): Boolean =
    root.flatten().any {
        it.text?.contains(query, ignoreCase) == true || it.contentDescription?.contains(query, ignoreCase) == true
    }
