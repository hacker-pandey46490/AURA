package com.aura.core.model

/**
 * Semantic description of a UI element. All provided fields must match (AND).
 * Text and description match case-insensitively as substrings; viewId matches the full id or its short name.
 */
data class Selector(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
) {
    fun isEmpty(): Boolean = text == null && contentDescription == null && viewId == null

    fun matches(nodeText: String?, nodeDescription: String?, nodeViewId: String?): Boolean {
        if (isEmpty()) return false
        if (text != null && nodeText?.contains(text, ignoreCase = true) != true) return false
        if (contentDescription != null && nodeDescription?.contains(contentDescription, ignoreCase = true) != true) return false
        if (viewId != null && !(nodeViewId == viewId || nodeViewId?.endsWith("/$viewId") == true)) return false
        return true
    }

    fun describe(): String = buildList {
        text?.let { add("text \"$it\"") }
        contentDescription?.let { add("desc \"$it\"") }
        viewId?.let { add("id $it") }
    }.joinToString(" + ")
}
