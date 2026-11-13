package com.screentranslator.app.engine

import android.content.Context

enum class EngineMode(val displayName: String) {
    OFFLINE_MLKIT("纯离线 (Google ML Kit)"),
    CLOUD_VISION_LLM("云端大模型 (高精度识图)")
}

data class CloudConfig(
    val mode: EngineMode = EngineMode.OFFLINE_MLKIT,
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini"
)

class CloudConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences("translator_config", Context.MODE_PRIVATE)

    fun load(): CloudConfig {
        val modeName = prefs.getString(KEY_MODE, EngineMode.OFFLINE_MLKIT.name)
        val mode = runCatching { EngineMode.valueOf(modeName ?: "") }
            .getOrDefault(EngineMode.OFFLINE_MLKIT)
        return CloudConfig(
            mode = mode,
            baseUrl = prefs.getString(KEY_BASE_URL, "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            model = prefs.getString(KEY_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini"
        )
    }

    fun save(config: CloudConfig) {
        prefs.edit()
            .putString(KEY_MODE, config.mode.name)
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_MODEL, config.model.trim())
            .apply()
    }

    private companion object {
        const val KEY_MODE = "engine_mode"
        const val KEY_BASE_URL = "cloud_base_url"
        const val KEY_API_KEY = "cloud_api_key"
        const val KEY_MODEL = "cloud_model"
    }
}
