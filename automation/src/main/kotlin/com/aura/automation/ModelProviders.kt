package com.aura.automation

import com.aura.core.brain.ChatMessage
import com.aura.core.brain.ChatRole
import com.aura.core.brain.ModelProvider
import com.aura.core.brain.ModelResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private data class HttpReply(val code: Int, val body: String)

private fun postJson(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): HttpReply {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        return HttpReply(code, text)
    } finally {
        connection.disconnect()
    }
}

/** Pulls a readable message out of an API error body without ever echoing request headers or keys. */
private fun errorText(reply: HttpReply): String {
    val detail = try {
        val error = JSONObject(reply.body).opt("error")
        when (error) {
            is JSONObject -> error.optString("message")
            is String -> error
            else -> ""
        }
    } catch (e: JSONException) {
        ""
    }
    val shown = detail.ifBlank { reply.body.take(160) }
    val hint = when (reply.code) {
        401, 403 -> " (check the API key)"
        404 -> " (check the model name and URL)"
        429 -> " (rate limit or no credit)"
        else -> ""
    }
    return "HTTP ${reply.code}: $shown$hint".trim()
}

private fun roleName(role: ChatRole): String = when (role) {
    ChatRole.SYSTEM -> "system"
    ChatRole.USER -> "user"
    ChatRole.ASSISTANT -> "assistant"
}

/** Works with OpenAI and any server that speaks the same /chat/completions API (OpenRouter, Groq, Ollama, llama.cpp, ...). */
class OpenAiCompatibleProvider(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String?,
    private val timeoutMs: Int = 60_000,
) : ModelProvider {

    override suspend fun complete(messages: List<ChatMessage>): ModelResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("model", model)
                .put("temperature", 0.2)
                .put(
                    "messages",
                    JSONArray().apply {
                        messages.forEach { put(JSONObject().put("role", roleName(it.role)).put("content", it.content)) }
                    },
                )
            val headers = if (apiKey.isNullOrBlank()) emptyMap() else mapOf("Authorization" to "Bearer $apiKey")
            val reply = postJson(baseUrl.trimEnd('/') + "/chat/completions", headers, body.toString(), timeoutMs)
            if (reply.code !in 200..299) return@withContext ModelResult.Error(errorText(reply))
            val text = JSONObject(reply.body)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            ModelResult.Success(text)
        } catch (e: IOException) {
            ModelResult.Error("Network problem: ${e.message}")
        } catch (e: JSONException) {
            ModelResult.Error("Unexpected reply from the server")
        }
    }
}

/** Anthropic Messages API. */
class AnthropicProvider(
    private val model: String,
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com",
    private val timeoutMs: Int = 60_000,
) : ModelProvider {

    override suspend fun complete(messages: List<ChatMessage>): ModelResult = withContext(Dispatchers.IO) {
        try {
            val system = messages.filter { it.role == ChatRole.SYSTEM }.joinToString("\n\n") { it.content }
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 1024)
                .put(
                    "messages",
                    JSONArray().apply {
                        messages.filter { it.role != ChatRole.SYSTEM }.forEach {
                            put(JSONObject().put("role", roleName(it.role)).put("content", it.content))
                        }
                    },
                )
            if (system.isNotBlank()) body.put("system", system)
            val headers = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")
            val reply = postJson(baseUrl.trimEnd('/') + "/v1/messages", headers, body.toString(), timeoutMs)
            if (reply.code !in 200..299) return@withContext ModelResult.Error(errorText(reply))
            val blocks = JSONObject(reply.body).getJSONArray("content")
            val text = buildString {
                for (i in 0 until blocks.length()) {
                    val block = blocks.getJSONObject(i)
                    if (block.optString("type") == "text") append(block.optString("text"))
                }
            }
            ModelResult.Success(text)
        } catch (e: IOException) {
            ModelResult.Error("Network problem: ${e.message}")
        } catch (e: JSONException) {
            ModelResult.Error("Unexpected reply from the server")
        }
    }
}
