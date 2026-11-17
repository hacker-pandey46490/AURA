package com.aura.core.brain

import com.aura.core.graph.GraphScriptParser
import com.aura.core.model.Action
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class ScriptedProvider(private val replies: List<ModelResult>) : ModelProvider {
    val received = mutableListOf<List<ChatMessage>>()
    override suspend fun complete(messages: List<ChatMessage>): ModelResult {
        received += messages
        return replies[minOf(received.size - 1, replies.lastIndex)]
    }
}

class PlannerTest {
    private fun ok(text: String) = ModelResult.Success(text)

    @Test
    fun validScriptBecomesAPlan() = runBlocking<Unit> {
        val provider = ScriptedProvider(listOf(ok("launch Settings\nwait 1000\nscroll forward => changed")))
        val result = PlannerSession(provider).send("open settings and scroll")
        assertTrue(result is PlanResult.Plan)
        assertEquals(3, (result as PlanResult.Plan).graph.roots.size)
        assertEquals("open settings and scroll", result.graph.title)
    }

    @Test
    fun codeFencesAreStripped() = runBlocking<Unit> {
        val provider = ScriptedProvider(listOf(ok("```\nlaunch Settings\nenter\n```")))
        val result = PlannerSession(provider).send("x")
        assertTrue(result is PlanResult.Plan)
        assertEquals("launch Settings\nenter", (result as PlanResult.Plan).script)
    }

    @Test
    fun invalidScriptIsSentBackForFixing() = runBlocking<Unit> {
        val provider = ScriptedProvider(listOf(ok("fly away"), ok("launch Settings")))
        val result = PlannerSession(provider).send("x")
        assertTrue(result is PlanResult.Plan)
        assertEquals(2, provider.received.size)
        val lastUserMessage = provider.received[1].last().content
        assertTrue(lastUserMessage.contains("Line 1"))
    }

    @Test
    fun givesUpAfterTooManyBadReplies() = runBlocking<Unit> {
        val provider = ScriptedProvider(listOf(ok("fly away")))
        val result = PlannerSession(provider, maxFixAttempts = 1).send("x")
        assertTrue(result is PlanResult.Failure)
        assertEquals(2, provider.received.size)
    }

    @Test
    fun askBecomesAQuestionAndConversationContinues() = runBlocking<Unit> {
        val provider = ScriptedProvider(listOf(ok("ASK: Which app should I open?"), ok("launch YouTube")))
        val session = PlannerSession(provider)
        val first = session.send("open the video app")
        assertEquals(PlanResult.Question("Which app should I open?"), first)
        val second = session.send("YouTube")
        assertTrue(second is PlanResult.Plan)
        val roles = provider.received[1].map { it.role }
        assertEquals(listOf(ChatRole.SYSTEM, ChatRole.USER, ChatRole.ASSISTANT, ChatRole.USER), roles)
    }

    @Test
    fun providerErrorBecomesFailure() = runBlocking<Unit> {
        val provider = ScriptedProvider(listOf(ModelResult.Error("no network")))
        assertEquals(PlanResult.Failure("no network"), PlannerSession(provider).send("x"))
    }

    @Test
    fun appNamesAreOnlyIncludedWhenGiven() {
        assertTrue(!PlannerPrompt.system(emptyList()).contains("INSTALLED APPS"))
        assertTrue(PlannerPrompt.system(listOf("YouTube", "Settings")).contains("YouTube, Settings"))
    }

    @Test
    fun enterCommandParses() {
        val r = GraphScriptParser.parse("type = hello\nenter")
        assertTrue(r.errors.isEmpty())
        assertEquals(Action.Enter, (r.graph.roots[1] as com.aura.core.graph.ActionNode).step.action)
    }
}
