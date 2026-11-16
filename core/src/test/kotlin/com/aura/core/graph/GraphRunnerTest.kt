package com.aura.core.graph

import com.aura.core.api.ActionExecutor
import com.aura.core.api.ScreenObserver
import com.aura.core.model.Action
import com.aura.core.model.ActionResult
import com.aura.core.model.Bounds
import com.aura.core.model.Expectation
import com.aura.core.model.ScreenSnapshot
import com.aura.core.model.StepState
import com.aura.core.model.TaskRunner
import com.aura.core.model.TaskStep
import com.aura.core.model.UiNode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun gSnap(vararg texts: String): ScreenSnapshot {
    val kids = texts.map { UiNode("android.widget.TextView", it, null, null, Bounds(0, 0, 1, 1), false, false, false, true) }
    return ScreenSnapshot("pkg", 0L, UiNode("android.widget.FrameLayout", null, null, null, Bounds(0, 0, 1, 1), false, false, false, true, kids))
}

private class GObserver(private val snap: ScreenSnapshot?) : ScreenObserver {
    override suspend fun capture(): ScreenSnapshot? = snap
}

private class GExecutor(private val results: List<ActionResult>) : ActionExecutor {
    val actions = mutableListOf<Action>()
    override suspend fun execute(action: Action): ActionResult {
        val r = results[minOf(actions.size, results.lastIndex)]
        actions += action
        return r
    }
}

class GraphRunnerTest {
    private val ok = ActionResult(true, "ok")
    private val bad = ActionResult(false, "nope")
    private val statuses = mutableMapOf<String, NodeStatus>()

    private fun runner(exec: ActionExecutor, obs: ScreenObserver, control: RunControl, hold: Boolean): GraphRunner {
        val taskRunner = TaskRunner(obs, exec, pause = { }, settleMs = 0, retryDelayMs = 0)
        return GraphRunner(
            taskRunner = taskRunner,
            observer = obs,
            control = control,
            listener = { id, st -> statuses[id] = st },
            holdOnFailure = hold,
        )
    }

    private fun act(id: String, action: Action, retries: Int = 2, optional: Boolean = false) =
        ActionNode(id, TaskStep(action, Expectation.None, retries), optional)

    @Test
    fun loopRepeatsItsChildren() = runBlocking<Unit> {
        val exec = GExecutor(listOf(ok))
        val graph = TaskGraph("t", listOf(LoopNode("l", 3, listOf(act("a", Action.Back), act("b", Action.Home)))))
        val outcome = runner(exec, GObserver(gSnap("x")), RunControl(), false).run(graph)
        assertEquals(GraphOutcome.Completed, outcome)
        assertEquals(listOf(Action.Back, Action.Home, Action.Back, Action.Home, Action.Back, Action.Home), exec.actions)
        assertEquals(3, statuses["l"]!!.iteration)
        assertEquals(StepState.SUCCEEDED, statuses["l"]!!.state)
    }

    @Test
    fun conditionRunsOnlyTheMatchingBranch() = runBlocking<Unit> {
        val exec = GExecutor(listOf(ok))
        val cond = ConditionNode(
            "c",
            Expectation.TextVisible("Foo"),
            GroupNode("t", "then", listOf(act("a", Action.Back))),
            GroupNode("e", "else", listOf(act("b", Action.Home))),
        )
        val outcome = runner(exec, GObserver(gSnap("Foo")), RunControl(), false).run(TaskGraph("t", listOf(cond)))
        assertEquals(GraphOutcome.Completed, outcome)
        assertEquals(listOf<Action>(Action.Back), exec.actions)
        assertEquals(StepState.SKIPPED, statuses["e"]!!.state)
        assertEquals(StepState.SKIPPED, statuses["b"]!!.state)
        assertEquals(StepState.SUCCEEDED, statuses["a"]!!.state)
    }

    @Test
    fun optionalStepFailureIsSkippedNotFatal() = runBlocking<Unit> {
        val exec = GExecutor(listOf(bad, ok))
        val graph = TaskGraph("t", listOf(act("a", Action.Back, retries = 0, optional = true), act("b", Action.Home)))
        val outcome = runner(exec, GObserver(gSnap("x")), RunControl(), false).run(graph)
        assertEquals(GraphOutcome.Completed, outcome)
        assertEquals(StepState.SKIPPED, statuses["a"]!!.state)
        assertEquals(StepState.SUCCEEDED, statuses["b"]!!.state)
    }

    @Test
    fun failureStopsTheTaskWhenNotHolding() = runBlocking<Unit> {
        val exec = GExecutor(listOf(bad))
        val graph = TaskGraph("t", listOf(act("a", Action.Back, retries = 0), act("b", Action.Home)))
        val outcome = runner(exec, GObserver(gSnap("x")), RunControl(), false).run(graph)
        assertTrue(outcome is GraphOutcome.Failed)
        assertEquals("a", (outcome as GraphOutcome.Failed).nodeId)
        assertEquals(StepState.FAILED, statuses["a"]!!.state)
        assertEquals(StepState.PENDING, statuses["b"]!!.state)
    }

    @Test
    fun userCanRetryAFailedStep() = runBlocking<Unit> {
        val exec = GExecutor(listOf(bad, ok))
        val control = RunControl()
        val graph = TaskGraph("t", listOf(act("a", Action.Back, retries = 0)))
        launch {
            control.awaitingDecision.first { it }
            control.decide(FailureDecision.RETRY)
        }
        val outcome = runner(exec, GObserver(gSnap("x")), control, true).run(graph)
        assertEquals(GraphOutcome.Completed, outcome)
        assertEquals(2, exec.actions.size)
        assertEquals(StepState.SUCCEEDED, statuses["a"]!!.state)
    }

    @Test
    fun userCanStopAtAFailedStep() = runBlocking<Unit> {
        val exec = GExecutor(listOf(bad))
        val control = RunControl()
        val graph = TaskGraph("t", listOf(act("a", Action.Back, retries = 0), act("b", Action.Home)))
        launch {
            control.awaitingDecision.first { it }
            control.decide(FailureDecision.STOP)
        }
        val outcome = runner(exec, GObserver(gSnap("x")), control, true).run(graph)
        assertEquals(GraphOutcome.Stopped, outcome)
        assertEquals(1, exec.actions.size)
    }

    @Test
    fun skipNextWhilePausedSkipsTheNextNode() = runBlocking<Unit> {
        val exec = GExecutor(listOf(ok))
        val control = RunControl()
        control.pause()
        val graph = TaskGraph("t", listOf(act("a", Action.Back), act("b", Action.Home)))
        launch { control.requestSkipNext() }
        val outcome = runner(exec, GObserver(gSnap("x")), control, false).run(graph)
        assertEquals(GraphOutcome.Completed, outcome)
        assertEquals(StepState.SKIPPED, statuses["a"]!!.state)
        assertEquals(listOf<Action>(Action.Home), exec.actions)
    }
}
