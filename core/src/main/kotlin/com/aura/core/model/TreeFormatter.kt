package com.aura.core.model

/** Renders a snapshot as an indented, human-readable tree (truncated at [maxChars]). */
fun ScreenSnapshot.toPrettyString(maxChars: Int = 20_000): String {
    val sb = StringBuilder()
    sb.append("package=").append(packageName).append('\n')
    val complete = appendNode(root, 0, sb, maxChars)
    if (!complete) sb.append("... truncated\n")
    return sb.toString()
}

private fun appendNode(node: UiNode, depth: Int, sb: StringBuilder, maxChars: Int): Boolean {
    if (sb.length >= maxChars) return false
    repeat(depth) { sb.append("  ") }
    sb.append(node.className.substringAfterLast('.'))
    node.text?.takeIf { it.isNotEmpty() }?.let { sb.append(" text=\"").append(it).append('"') }
    node.contentDescription?.takeIf { it.isNotEmpty() }?.let { sb.append(" desc=\"").append(it).append('"') }
    node.viewId?.let { sb.append(" id=").append(it) }
    val flags = buildList {
        if (node.clickable) add("click")
        if (node.scrollable) add("scroll")
        if (node.editable) add("edit")
        if (!node.enabled) add("disabled")
    }
    if (flags.isNotEmpty()) sb.append(" [").append(flags.joinToString(",")).append(']')
    sb.append('\n')
    for (child in node.children) {
        if (!appendNode(child, depth + 1, sb, maxChars)) return false
    }
    return true
}
