package com.aura.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.aura.core.model.Bounds
import com.aura.core.model.UiNode

internal object NodeMapper {
    private const val MAX_DEPTH = 40
    private const val REDACTED = "<redacted>"

    fun map(node: AccessibilityNodeInfo, depth: Int = 0): UiNode {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val children = if (depth >= MAX_DEPTH) {
            emptyList()
        } else {
            (0 until node.childCount).mapNotNull { i -> node.getChild(i)?.let { map(it, depth + 1) } }
        }
        return UiNode(
            className = node.className?.toString().orEmpty(),
            // Never capture the contents of password fields.
            text = if (node.isPassword) REDACTED else node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewId = node.viewIdResourceName,
            bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            clickable = node.isClickable,
            scrollable = node.isScrollable,
            editable = node.isEditable,
            enabled = node.isEnabled,
            children = children,
        )
    }
}
