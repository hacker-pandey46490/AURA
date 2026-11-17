package com.aura.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.aura.core.api.ActionExecutor
import com.aura.core.api.ScreenObserver
import com.aura.core.model.Action
import com.aura.core.model.ActionResult
import com.aura.core.model.ScreenSnapshot
import com.aura.core.model.Selector
import com.aura.core.model.SwipeDirection
import com.aura.core.model.describe
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class AccessibilityScreenObserver(private val service: AuraAccessibilityService) : ScreenObserver {
    override suspend fun capture(): ScreenSnapshot? = service.captureSnapshot()
}

internal class AccessibilityActionExecutor(private val service: AuraAccessibilityService) : ActionExecutor {

    override suspend fun execute(action: Action): ActionResult = when (action) {
        Action.Back -> global(AccessibilityService.GLOBAL_ACTION_BACK, "Back")
        Action.Home -> global(AccessibilityService.GLOBAL_ACTION_HOME, "Home")
        Action.ScrollForward -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, "forward")
        Action.ScrollBackward -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, "backward")
        Action.Enter -> pressEnter()
        is Action.ClickByText -> clickByText(action.text)
        is Action.Click -> click(action.selector)
        is Action.LongClick -> longClick(action.selector)
        is Action.DoubleTap -> doubleTap(action.selector)
        is Action.TypeText -> typeText(action.selector, action.text)
        is Action.Swipe -> swipe(action.direction)
        is Action.LaunchApp -> launchApp(action.appName)
        is Action.Wait -> {
            delay(action.millis)
            ActionResult(true, "Waited ${action.millis} ms")
        }
    }

    // ---- global actions -------------------------------------------------------------------------

    private fun global(code: Int, name: String): ActionResult {
        val ok = service.performGlobalAction(code)
        return ActionResult(ok, if (ok) "$name performed" else "$name failed")
    }

    // ---- scrolling ------------------------------------------------------------------------------

    private fun scroll(accessibilityAction: Int, name: String): ActionResult {
        val root = service.rootInActiveWindow ?: return noWindow()
        val target = findFirst(root) { it.isScrollable }
            ?: return ActionResult(false, "No scrollable element found")
        val ok = target.performAction(accessibilityAction)
        return ActionResult(ok, if (ok) "Scrolled $name" else "Scroll $name was rejected")
    }

    // ---- clicking -------------------------------------------------------------------------------

    private fun clickByText(text: String): ActionResult {
        val root = service.rootInActiveWindow ?: return noWindow()
        val match = root.findAccessibilityNodeInfosByText(text).firstOrNull()
            ?: return ActionResult(false, "No element with text \"$text\"")
        val clickable = ancestorOrSelf(match) { it.isClickable }
        val ok = clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        return ActionResult(ok, if (ok) "Clicked \"$text\"" else "Could not click \"$text\"")
    }

    private suspend fun click(selector: Selector): ActionResult {
        val root = service.rootInActiveWindow ?: return noWindow()
        val node = findNode(root, selector) ?: return notFound(selector)
        val clickable = ancestorOrSelf(node) { it.isClickable }
        if (clickable != null && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return ActionResult(true, "Clicked ${selector.describe()}")
        }
        val (x, y) = centerOf(node)
        val ok = dispatch(tapGesture(x, y))
        return ActionResult(ok, if (ok) "Tapped ${selector.describe()} by gesture" else "Tap gesture failed")
    }

    private suspend fun longClick(selector: Selector): ActionResult {
        val root = service.rootInActiveWindow ?: return noWindow()
        val node = findNode(root, selector) ?: return notFound(selector)
        val target = ancestorOrSelf(node) { it.isLongClickable }
        if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            return ActionResult(true, "Long-clicked ${selector.describe()}")
        }
        val (x, y) = centerOf(node)
        val ok = dispatch(gesture(stroke(x, y, 0L, 800L)))
        return ActionResult(ok, if (ok) "Long-pressed ${selector.describe()} by gesture" else "Long-press gesture failed")
    }

    private suspend fun doubleTap(selector: Selector): ActionResult {
        val root = service.rootInActiveWindow ?: return noWindow()
        val node = findNode(root, selector) ?: return notFound(selector)
        val (x, y) = centerOf(node)
        val ok = dispatch(
            GestureDescription.Builder()
                .addStroke(stroke(x, y, 0L, 50L))
                .addStroke(stroke(x, y, 150L, 50L))
                .build(),
        )
        return ActionResult(ok, if (ok) "Double-tapped ${selector.describe()}" else "Double-tap gesture failed")
    }

    // ---- typing ---------------------------------------------------------------------------------

    private fun typeText(selector: Selector?, text: String): ActionResult {
        val root = service.rootInActiveWindow ?: return noWindow()
        val node = if (selector != null) {
            findNode(root, selector) ?: return notFound(selector)
        } else {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return ActionResult(false, "No focused input field")
        }
        if (!node.isEditable) return ActionResult(false, "Target is not an editable field")
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        // Never put the typed text into messages or logs.
        return ActionResult(ok, if (ok) "Typed ${text.length} characters" else "Typing was rejected")
    }

    private fun pressEnter(): ActionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ActionResult(false, "The Enter key needs Android 11 or newer")
        }
        val root = service.rootInActiveWindow ?: return noWindow()
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return ActionResult(false, "No focused input field")
        val ok = node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        return ActionResult(ok, if (ok) "Pressed Enter" else "Enter was rejected")
    }

    // ---- swiping --------------------------------------------------------------------------------

    private suspend fun swipe(direction: SwipeDirection): ActionResult {
        val dm = service.resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val path = Path()
        when (direction) {
            SwipeDirection.UP -> { path.moveTo(w / 2, h * 0.7f); path.lineTo(w / 2, h * 0.3f) }
            SwipeDirection.DOWN -> { path.moveTo(w / 2, h * 0.3f); path.lineTo(w / 2, h * 0.7f) }
            SwipeDirection.LEFT -> { path.moveTo(w * 0.8f, h / 2); path.lineTo(w * 0.2f, h / 2) }
            SwipeDirection.RIGHT -> { path.moveTo(w * 0.2f, h / 2); path.lineTo(w * 0.8f, h / 2) }
        }
        val ok = dispatch(gesture(GestureDescription.StrokeDescription(path, 0L, 350L)))
        return ActionResult(ok, if (ok) "Swiped ${direction.name.lowercase()}" else "Swipe gesture failed")
    }

    // ---- launching apps -------------------------------------------------------------------------

    private fun launchApp(name: String): ActionResult {
        val pm = service.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = pm.queryIntentActivities(query, 0).map { it to it.loadLabel(pm).toString() }
        val match = apps.firstOrNull { it.second.equals(name, ignoreCase = true) }
            ?: apps.firstOrNull { it.second.contains(name, ignoreCase = true) }
            ?: return ActionResult(false, "No launchable app named \"$name\"")
        val intent = pm.getLaunchIntentForPackage(match.first.activityInfo.packageName)
            ?: return ActionResult(false, "\"${match.second}\" has no launch intent")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            service.startActivity(intent)
            ActionResult(true, "Launched ${match.second}")
        } catch (e: Exception) {
            ActionResult(false, "Could not launch ${match.second}: ${e.message}")
        }
    }

    // ---- helpers --------------------------------------------------------------------------------

    private fun noWindow() = ActionResult(false, "No active window (protected screen?)")

    private fun notFound(selector: Selector) =
        ActionResult(false, "No visible element matching ${selector.describe()}")

    private fun findNode(root: AccessibilityNodeInfo, selector: Selector): AccessibilityNodeInfo? =
        findFirst(root) {
            it.isVisibleToUser &&
                selector.matches(it.text?.toString(), it.contentDescription?.toString(), it.viewIdResourceName)
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

    private fun ancestorOrSelf(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null && !predicate(current)) current = current.parent
        return current
    }

    private fun centerOf(node: AccessibilityNodeInfo): Pair<Float, Float> {
        val r = Rect()
        node.getBoundsInScreen(r)
        return Pair(r.exactCenterX(), r.exactCenterY())
    }

    private fun stroke(x: Float, y: Float, startMs: Long, durationMs: Long): GestureDescription.StrokeDescription {
        val path = Path().apply { moveTo(x, y) }
        return GestureDescription.StrokeDescription(path, startMs, durationMs)
    }

    private fun tapGesture(x: Float, y: Float): GestureDescription = gesture(stroke(x, y, 0L, 50L))

    private fun gesture(stroke: GestureDescription.StrokeDescription): GestureDescription =
        GestureDescription.Builder().addStroke(stroke).build()

    private suspend fun dispatch(gesture: GestureDescription): Boolean = suspendCancellableCoroutine { cont ->
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            },
            null,
        )
        if (!accepted && cont.isActive) cont.resume(false)
    }
}
