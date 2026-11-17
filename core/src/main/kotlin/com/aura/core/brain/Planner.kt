package com.aura.core.brain

import com.aura.core.graph.GraphScriptParser
import com.aura.core.graph.TaskGraph
import com.aura.core.graph.flattened

sealed interface PlanResult {
    data class Plan(val graph: TaskGraph, val script: String) : PlanResult
    data class Question(val text: String) : PlanResult
    data class Failure(val message: String) : PlanResult
}

/**
 * One planning conversation. The model's reply is either a question ("ASK: ...") or a script in
 * AURA's own task language. Scripts are validated by the same parser the Script tab uses; if they
 * have errors the model is asked to fix them (up to [maxFixAttempts] times). A plan is never run
 * automatically: the caller must show it to the user first.
 */
class PlannerSession(
    private val provider: ModelProvider,
    appNames: List<String> = emptyList(),
    private val maxFixAttempts: Int = 2,
) {
    private val history = mutableListOf(ChatMessage(ChatRole.SYSTEM, PlannerPrompt.system(appNames)))
    private var firstRequest: String? = null

    suspend fun send(userText: String): PlanResult {
        if (firstRequest == null) firstRequest = userText
        history += ChatMessage(ChatRole.USER, userText)
        var fixes = 0
        while (true) {
            when (val result = provider.complete(history.toList())) {
                is ModelResult.Error -> return PlanResult.Failure(result.message)
                is ModelResult.Success -> {
                    val reply = cleanReply(result.text)
                    history += ChatMessage(ChatRole.ASSISTANT, reply)

                    if (reply.startsWith("ASK:", ignoreCase = true)) {
                        val question = reply.substring(4).trim()
                        return if (question.isEmpty()) {
                            PlanResult.Failure("The AI sent an empty question")
                        } else {
                            PlanResult.Question(question)
                        }
                    }

                    val parsed = GraphScriptParser.parse(reply, titleFor(firstRequest.orEmpty()))
                    val problems = when {
                        parsed.errors.isNotEmpty() -> parsed.errors
                        parsed.graph.roots.isEmpty() -> listOf("The script has no steps")
                        parsed.graph.flattened().size > MAX_NODES -> listOf("The script is too long (more than $MAX_NODES steps)")
                        else -> emptyList()
                    }
                    if (problems.isEmpty()) return PlanResult.Plan(parsed.graph, reply)

                    if (fixes >= maxFixAttempts) {
                        return PlanResult.Failure("The AI could not produce a valid plan: " + problems.first())
                    }
                    fixes++
                    history += ChatMessage(
                        ChatRole.USER,
                        "That script has problems:\n" + problems.joinToString("\n") +
                            "\nReply again with ONLY the corrected script.",
                    )
                }
            }
        }
    }

    private fun titleFor(request: String): String {
        val oneLine = request.trim().replace('\n', ' ')
        return if (oneLine.length <= 40) oneLine.ifEmpty { "Task" } else oneLine.take(40) + "..."
    }

    companion object {
        const val MAX_NODES = 80

        /** Removes markdown code fences the model may add despite instructions. */
        fun cleanReply(text: String): String =
            text.lines()
                .filterNot { it.trim().startsWith("```") }
                .joinToString("\n")
                .trim()
    }
}
