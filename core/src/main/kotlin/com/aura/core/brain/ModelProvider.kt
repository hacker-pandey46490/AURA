package com.aura.core.brain

enum class ChatRole { SYSTEM, USER, ASSISTANT }

data class ChatMessage(val role: ChatRole, val content: String)

sealed interface ModelResult {
    data class Success(val text: String) : ModelResult
    data class Error(val message: String) : ModelResult
}

enum class ProviderKind(val displayName: String) {
    ANTHROPIC("Anthropic (Claude)"),
    OPENAI_COMPATIBLE("OpenAI-compatible (cloud or local server)"),
}

/** Provider-independent language model. Implementations must never include API keys in error messages. */
interface ModelProvider {
    suspend fun complete(messages: List<ChatMessage>): ModelResult
}
