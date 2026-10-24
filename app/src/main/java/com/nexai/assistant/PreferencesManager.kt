package com.nexai.assistant

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nex_ai_prefs", Context.MODE_PRIVATE)

    // Language Preference: Hindi, Hinglish, English, Urdu, Bengali, Punjabi
    var preferredLanguage: String
        get() = prefs.getString("preferred_language", "Hinglish") ?: "Hinglish"
        set(value) = prefs.edit().putString("preferred_language", value).apply()

    // 6 Voice Presets: JARVIS Deep, FRIDAY Female, Classic Nex, Fast Crisp, Calm Soft, Cyber Metallic
    var voiceStyle: String
        get() = prefs.getString("voice_style", "JARVIS Deep") ?: "JARVIS Deep"
        set(value) = prefs.edit().putString("voice_style", value).apply()

    // AI Personality Tone: JARVIS Tactical, Friendly Buddy, Short & Direct
    var personalityTone: String
        get() = prefs.getString("personality_tone", "JARVIS Tactical") ?: "JARVIS Tactical"
        set(value) = prefs.edit().putString("personality_tone", value).apply()

    // Spoken Voice Toggle
    var isTtsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
        set(value) = prefs.edit().putBoolean("tts_enabled", value).apply()

    // Haptic / Vibration Feedback
    var isHapticEnabled: Boolean
        get() = prefs.getBoolean("haptic_enabled", true)
        set(value) = prefs.edit().putBoolean("haptic_enabled", value).apply()
}
