package com.aura.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.aura.core.graph.FailureDecision
import com.aura.core.graph.GraphOutcome
import com.aura.core.graph.TaskGraph
import com.aura.core.graph.flattened
import com.aura.core.model.Action
import com.aura.core.model.ScreenSnapshot
import com.aura.core.model.TaskOutcome
import com.aura.core.model.TaskStep
import com.aura.core.model.toPrettyString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AuraAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var taskJob: Job? = null
    private lateinit var overlay: StopOverlay
    private lateinit var observer: AccessibilityScreenObserver
    private lateinit var executor: AccessibilityActionExecutor
    private lateinit var factory: AgentFactory

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = StopOverlay(this) { action -> handleOverlayAction(action) }
        observer = AccessibilityScreenObserver(this)
        executor = AccessibilityActionExecutor(this)
        factory = AgentFactory(observer, executor, AuraRuntime.control)
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

    /** Phase 1 demo: dump the tree, scroll once, dump again and verify the screen changed. */
    fun startDemo() {
        if (taskJob?.isActive == true) return
        taskJob = scope.launch {
            AuraRuntime.begin("Settings demo")
            AuraRuntime.initProgress(emptyList())
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

    /** Phase 2 flat runner, kept for compatibility. The UI now uses [startGraph]. */
    fun startTask(steps: List<TaskStep>) {
        if (steps.isEmpty() || taskJob?.isActive == true) return
        taskJob = scope.launch {
            AuraRuntime.begin("task (${steps.size} steps)")
            AuraRuntime.initProgress(steps)
            overlay.show()
            try {
                val runner = factory.createTaskRunner(pause = { delay(it) })
                when (val outcome = runner.run(steps) { AuraRuntime.updateStep(it) }) {
                    TaskOutcome.Completed ->
                        AuraRuntime.finish(true, "All ${steps.size} steps completed and verified")
                    is TaskOutcome.Failed ->
                        AuraRuntime.finish(false, "Step ${outcome.stepIndex + 1} failed: ${outcome.reason}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AuraRuntime.finish(success = false, message = "Failed: ${e.message}")
            } finally {
                overlay.hide()
            }
        }
    }

    /**
     * Runs a task graph. The caller (UI) is responsible for user approval beforehand.
     * With [holdOnFailure] a failed step pauses the run and asks Retry / Skip / Stop.
     */
    fun startGraph(graph: TaskGraph, holdOnFailure: Boolean) {
        if (graph.roots.isEmpty() || taskJob?.isActive == true) return
        taskJob = scope.launch {
            AuraRuntime.begin(graph.title)
            AuraRuntime.initGraph(graph)
            AuraRuntime.control.reset()
            overlay.show()
            val modeJob = launch {
                combine(AuraRuntime.control.paused, AuraRuntime.control.awaitingDecision) { paused, deciding ->
                    when {
                        deciding -> OverlayMode.DECISION
                        paused -> OverlayMode.PAUSED
                        else -> OverlayMode.RUNNING
                    }
                }.collect { overlay.setMode(it) }
            }
            try {
                val runner = factory.createGraphRunner(
                    holdOnFailure = holdOnFailure,
                    pause = { delay(it) },
                    listener = { id, status -> AuraRuntime.updateNode(id, status) },
                )
                when (val outcome = runner.run(graph)) {
                    GraphOutcome.Completed -> AuraRuntime.finish(true, "Task completed")
                    is GraphOutcome.Failed -> {
                        val title = graph.flattened().firstOrNull { it.node.id == outcome.nodeId }?.node?.title ?: outcome.nodeId
                        AuraRuntime.finish(false, "Failed at \"$title\": ${outcome.reason}")
                    }
                    GraphOutcome.Stopped -> AuraRuntime.markStopped()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AuraRuntime.finish(success = false, message = "Failed: ${e.message}")
            } finally {
                modeJob.cancel()
                overlay.hide()
            }
        }
    }

    fun stopAutomation() {
        taskJob?.cancel()
        taskJob = null
        if (::overlay.isInitialized) overlay.hide()
        AuraRuntime.markStopped()
    }

    private fun handleOverlayAction(action: OverlayAction) {
        val control = AuraRuntime.control
        when (action) {
            OverlayAction.PAUSE -> control.pause()
            OverlayAction.RESUME -> control.resume()
            OverlayAction.SKIP_NEXT -> control.requestSkipNext()
            OverlayAction.RETRY -> control.decide(FailureDecision.RETRY)
            OverlayAction.SKIP -> control.decide(FailureDecision.SKIP)
            OverlayAction.STOP -> stopAutomation()
        }
    }

    companion object {
        @Volatile
        var instance: AuraAccessibilityService? = null
            private set
    }
}
