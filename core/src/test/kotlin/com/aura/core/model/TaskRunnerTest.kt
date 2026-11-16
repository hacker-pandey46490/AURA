package com.aura.core.model

import com.aura.core.api.ActionExecutor
import com.aura.core.api.ScreenObserver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun runnerSnap(vararg texts: String): ScreenSnapshot {
    val kids = texts.map { UiNode("android.widget.TextView", it, null, null, Bounds(0, 0, 1, 1), false, false, false, true) }
    return ScreenSnapshot("pkg", 0L, UiNode("android.widget.FrameLayout", null, null, null, Bounds(0, 0, 1, 1), false, false, false, true, kids))
}

private class ScriptedObserver(private val snaps: List<ScreenSnapshot?>) : ScreenObserver {
    private var i = 0
    override suspend fun capture(): ScreenSnapshot? = snaps[minOf(i++, snaps.lastIndex)]
}

private class ScriptedExecutor(private val results: List<ActionResult>) : ActionExecutor {
    var calls = 0
    override suspend fun execute(action: Action): ActionResult = results[minOf(calls++, results.lastIndex)]
}

class TaskRunnerTest {
    private val ok = ActionResult(true, "ok")
    private val bad = ActionResult(false, "nope")

    private fun runner(obs: ScreenObserver, exec: ActionExecutor) =
        TaskRunner(obs, exec, pause = { }, settleMs = 0, retryDelayMs = 0)

    @Test
    fun completesWhenEveryStepSucceeds() = runBlocking<Unit> {
        val exec = ScriptedExecutor(listOf(ok))
        val outcome = runner(ScriptedObserver(listOf(runnerSnap("a"))), exec)
            .run(listOf(TaskStep(Action.Back), TaskStep(Action.Home))) { }
        assertEquals(TaskOutcome.Completed, outcome)
        assertEquals(2, exec.calls)
    }

    @Test
    fun retriesActionThatFailsThenSucceeds() = runBlocking<Unit> {
        val exec = ScriptedExecutor(listOf(bad, bad, ok))
        val outcome = runner(ScriptedObserver(listOf(runnerSnap("a"))), exec)
            .run(listOf(TaskStep(Action.Back, maxRetries = 2))) { }
        assertEquals(TaskOutcome.Completed, outcome)
        assertEquals(3, exec.calls)
    }

    @Test
    fun failsAfterRetryLimit() = runBlocking<Unit> {
        val exec = ScriptedExecutor(listOf(bad))
        val outcome = runner(ScriptedObserver(listOf(runnerSnap("a"))), exec)
            .run(listOf(TaskStep(Action.Back, maxRetries = 2))) { }
        assertTrue(outcome is TaskOutcome.Failed)
        assertEquals(3, exec.calls)
    }

    @Test
    fun unmetExpectationDoesNotReExecuteTheAction() = runBlocking<Unit> {
        val exec = ScriptedExecutor(listOf(ok))
        val step = TaskStep(Action.Click(Selector(text = "x")), Expectation.TextVisible("Done"))
        val outcome = runner(ScriptedObserver(listOf(runnerSnap("a"))), exec).run(listOf(step)) { }
        assertTrue(outcome is TaskOutcome.Failed)
        assertEquals(1, exec.calls)
    }

    @Test
    fun expectationMetOnLaterVerificationSucceeds() = runBlocking<Unit> {
        val exec = ScriptedExecutor(listOf(ok))
        val step = TaskStep(Action.Click(Selector(text = "x")), Expectation.TextVisible("Done"))
        val obs = ScriptedObserver(listOf(runnerSnap("a"), runnerSnap("a"), runnerSnap("Done")))
        val outcome = runner(obs, exec).run(listOf(step)) { }
        assertEquals(TaskOutcome.Completed, outcome)
        assertEquals(1, exec.calls)
    }
}
