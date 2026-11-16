package com.aura.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.aura.core.model.Action
import com.aura.core.model.ScreenSnapshot
import com.aura.core.model.toPrettyString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AuraAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var demoJob: Job? = null
    private lateinit var overlay: StopOverlay
    private lateinit var observer: AccessibilityScreenObserver
    private lateinit var executor: AccessibilityActionExecutor

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = StopOverlay(this) { stopAutomation() }
        observer = AccessibilityScreenObserver(this)
        executor = AccessibilityActionExecutor(this)
        instance = this
        AuraRuntime.setServiceConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        stopAutomation()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopAutomation()
        instance = null
        AuraRuntime.setServiceConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Snapshot of the active window, or null if unavailable (e.g. protected screens). */
    fun captureSnapshot(): ScreenSnapshot? {
        val root = rootInActiveWindow ?: return null
        return ScreenSnapshot(
            packageName = root.packageName?.toString().orEmpty(),
            timestampMs = System.currentTimeMillis(),
            root = NodeMapper.map(root),
        )
    }

    /**
     * Phase 1 demo: the caller has just opened Settings. We dump the tree, scroll once,
     * dump again and verify the screen changed.
     */
    fun startDemo() {
        if (demoJob?.isActive == true) return
        demoJob = scope.launch {
            AuraRuntime.begin("Settings demo")
            overlay.show()
            try {
                delay(2000)
                val before = observer.capture() ?: error("No readable window (protected screen?)")
                AuraRuntime.publishDump(before.toPrettyString())

                val scroll = executor.execute(Action.ScrollForward)
                delay(800)
                val after = observer.capture()
                val changed = after != null && after.root != before.root
                val summary = when {
                    !scroll.success -> "Scroll step: ${scroll.message}"
                    changed -> "Scrolled and verified the screen changed"
                    else -> "Scrolled, but the screen did not change (maybe at the end of the list)"
                }
                AuraRuntime.finish(success = scroll.success, message = summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AuraRuntime.finish(success = false, message = "Failed: ${e.message}")
            } finally {
                overlay.hide()
            }
        }
    }

    fun stopAutomation() {
        demoJob?.cancel()
        demoJob = null
        if (::overlay.isInitialized) overlay.hide()
        AuraRuntime.markStopped()
    }

    companion object {
        @Volatile
        var instance: AuraAccessibilityService? = null
            private set
    }
}
