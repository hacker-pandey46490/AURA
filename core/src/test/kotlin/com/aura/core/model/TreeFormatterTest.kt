package com.aura.core.model

import org.junit.Assert.assertTrue
import org.junit.Test

class TreeFormatterTest {
    private fun node(cls: String, text: String? = null, clickable: Boolean = false, children: List<UiNode> = emptyList()) =
        UiNode(cls, text, null, null, Bounds(0, 0, 10, 10), clickable, false, false, true, children)

    @Test
    fun rendersIndentationTextAndFlags() {
        val snap = ScreenSnapshot(
            packageName = "com.example",
            timestampMs = 0L,
            root = node("android.widget.FrameLayout", children = listOf(node("android.widget.Button", "OK", clickable = true))),
        )
        val out = snap.toPrettyString()
        assertTrue(out.contains("package=com.example"))
        assertTrue(out.contains("FrameLayout\n"))
        assertTrue(out.contains("  Button text=\"OK\" [click]\n"))
    }

    @Test
    fun truncatesLargeTrees() {
        val kids = (1..500).map { node("android.widget.TextView", "item $it") }
        val snap = ScreenSnapshot("p", 0L, node("android.widget.LinearLayout", children = kids))
        val out = snap.toPrettyString(maxChars = 200)
        assertTrue(out.contains("truncated"))
    }
}
