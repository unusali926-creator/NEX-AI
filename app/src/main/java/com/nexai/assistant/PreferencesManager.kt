package com.nexai.assistant

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nex_ai_prefs", Context.MODE_PRIVATE)

    // Master Core State: Active (All-time listening & companion) vs Inactive (Sleeping)
    var isMasterActive: Boolean
        get() = prefs.getBoolean("master_core_active", false)
        set(value) = prefs.edit().putBoolean("master_core_active", value).apply()

    // Continuous Active Conversation Loop (Keep listening until told to stop)
    var isContinuousActive: Boolean
        get() = prefs.getBoolean("continuous_active", true)
        set(value) = prefs.edit().putBoolean("continuous_active", value).apply()

    // Preferred Language: Hindi, Hinglish, English, Urdu, Bengali, Punjabi
    var preferredLanguage: String
        get() = prefs.getString("preferred_language", "Hinglish") ?: "Hinglish"
        set(value) = prefs.edit().putString("preferred_language", value).apply()

    // 6 Voice Presets
    var voiceStyle: String
        get() = prefs.getString("voice_style", "JARVIS Deep") ?: "JARVIS Deep"
        set(value) = prefs.edit().putString("voice_style", value).apply()

    // AI Personality Tone
    var personalityTone: String
        get() = prefs.getString("personality_tone", "Friendly Buddy") ?: "Friendly Buddy"
        set(value) = prefs.edit().putString("personality_tone", value).apply()

    // Spoken Audio Toggle
    var isTtsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
        set(value) = prefs.edit().putBoolean("tts_enabled", value).apply()

    // Haptic Vibration Feedback
    var isHapticEnabled: Boolean
        get() = prefs.getBoolean("haptic_enabled", true)
        set(value) = prefs.edit().putBoolean("haptic_enabled", value).apply()

    // Owner Name (Always 'Sir' or customizable)
    var ownerName: String
        get() = prefs.getString("owner_name", "Sir") ?: "Sir"
        set(value) = prefs.edit().putString("owner_name", value).apply()
}
