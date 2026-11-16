package com.aura.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.aura.core.api.ActionExecutor
import com.aura.core.api.ScreenObserver
import com.aura.core.model.Action
import com.aura.core.model.ActionResult
import com.aura.core.model.ScreenSnapshot

internal class AccessibilityScreenObserver(private val service: AuraAccessibilityService) : ScreenObserver {
    override suspend fun capture(): ScreenSnapshot? = service.captureSnapshot()
}

internal class AccessibilityActionExecutor(private val service: AuraAccessibilityService) : ActionExecutor {

    override suspend fun execute(action: Action): ActionResult = when (action) {
        Action.Back -> global(AccessibilityService.GLOBAL_ACTION_BACK, "Back")
        Action.Home -> global(AccessibilityService.GLOBAL_ACTION_HOME, "Home")
        Action.ScrollForward -> scrollForward()
        is Action.ClickByText -> clickByText(action.text)
    }

    private fun global(code: Int, name: String): ActionResult {
        val ok = service.performGlobalAction(code)
        return ActionResult(ok, if (ok) "$name performed" else "$name failed")
    }

    private fun scrollForward(): ActionResult {
        val root = service.rootInActiveWindow ?: return ActionResult(false, "No active window")
        val target = findFirst(root) { it.isScrollable }
            ?: return ActionResult(false, "No scrollable element found")
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        return ActionResult(ok, if (ok) "Scrolled forward" else "Scroll was rejected")
    }

    private fun clickByText(text: String): ActionResult {
        val root = service.rootInActiveWindow ?: return ActionResult(false, "No active window")
        val match = root.findAccessibilityNodeInfosByText(text).firstOrNull()
            ?: return ActionResult(false, "No element with text \"$text\"")
        var node: AccessibilityNodeInfo? = match
        while (node != null && !node.isClickable) node = node.parent
        val ok = node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        return ActionResult(ok, if (ok) "Clicked \"$text\"" else "Could not click \"$text\"")
    }

    private fun findFirst(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findFirst(child, predicate)?.let { return it }
        }
        return null
    }
}
