package com.aura.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.aura.automation.AnthropicProvider
import com.aura.automation.OpenAiCompatibleProvider
import com.aura.core.brain.ModelProvider
import com.aura.core.brain.ProviderKind

data class AiSettings(
    val kind: ProviderKind = ProviderKind.ANTHROPIC,
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "claude-sonnet-5-5",
    val apiKey: String = "",
    val shareAppNames: Boolean = false,
) {
    private val isLocalServer: Boolean
        get() = baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1")

    val isConfigured: Boolean
        get() = model.isNotBlank() &&
            (apiKey.isNotBlank() || (kind == ProviderKind.OPENAI_COMPATIBLE && isLocalServer))
}

fun createProvider(settings: AiSettings): ModelProvider = when (settings.kind) {
    ProviderKind.ANTHROPIC -> AnthropicProvider(settings.model.trim(), settings.apiKey.trim())
    ProviderKind.OPENAI_COMPATIBLE ->
        OpenAiCompatibleProvider(settings.baseUrl.trim(), settings.model.trim(), settings.apiKey.trim())
}

/** Stores AI settings, including the API key, encrypted with a key held in the Android Keystore. */
class AiSettingsStore(context: Context) {
    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            "aura_ai_settings",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): AiSettings = try {
        val defaults = AiSettings()
        AiSettings(
            kind = runCatching { ProviderKind.valueOf(prefs.getString("kind", "") ?: "") }.getOrDefault(defaults.kind),
            baseUrl = prefs.getString("baseUrl", null) ?: defaults.baseUrl,
            model = prefs.getString("model", null) ?: defaults.model,
            apiKey = prefs.getString("apiKey", null) ?: "",
            shareAppNames = prefs.getBoolean("shareAppNames", false),
        )
    } catch (e: Exception) {
        AiSettings()
    }

    fun save(settings: AiSettings) {
        try {
            prefs.edit()
                .putString("kind", settings.kind.name)
                .putString("baseUrl", settings.baseUrl.trim())
                .putString("model", settings.model.trim())
                .putString("apiKey", settings.apiKey.trim())
                .putBoolean("shareAppNames", settings.shareAppNames)
                .apply()
        } catch (e: Exception) {
            // Storage failure: settings stay unsaved rather than crashing the app.
        }
    }
}
