package com.nexai.assistant

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nex_ai_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_api_key", value).apply()

    var isTtsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
        set(value) = prefs.edit().putBoolean("tts_enabled", value).apply()

    var preferredLanguage: String
        get() = prefs.getString("preferred_language", "Hinglish") ?: "Hinglish"
        set(value) = prefs.edit().putString("preferred_language", value).apply()
}
