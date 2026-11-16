package com.aura.core.model

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Platform-independent snapshot of one node in the on-screen UI hierarchy. */
data class UiNode(
    val className: String,
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val bounds: Bounds,
    val clickable: Boolean,
    val scrollable: Boolean,
    val editable: Boolean,
    val enabled: Boolean,
    val children: List<UiNode> = emptyList(),
)

data class ScreenSnapshot(
    val packageName: String,
    val timestampMs: Long,
    val root: UiNode,
)
