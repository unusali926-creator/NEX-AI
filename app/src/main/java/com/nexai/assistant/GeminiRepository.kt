package com.nexai.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GeminiRepository(private val prefs: PreferencesManager) {

    // Offline Instant Device Command Matcher
    private fun checkInstantLocalCommand(query: String): String? {
        val q = query.lowercase().trim()
        return when {
            q.contains("torch on") || q.contains("flashlight on") || q.contains("light on") || q.contains("flash on") ->
                "[ACTION:TORCH_ON]"
            q.contains("torch off") || q.contains("flashlight off") || q.contains("light band") || q.contains("flash off") || q.contains("light off") ->
                "[ACTION:TORCH_OFF]"
            q.contains("battery") || q.contains("charging") || q.contains("charge kitna") ->
                "[ACTION:BATTERY]"
            q.contains("volume full") || q.contains("awaaz badhao") || q.contains("sound full") || q.contains("max volume") ->
                "[ACTION:VOLUME_MAX]"
            q.contains("volume mute") || q.contains("silent") || q.contains("awaaz band") || q.contains("mute karo") ->
                "[ACTION:VOLUME_MUTE]"
            q.contains("whatsapp") ->
                "[ACTION:APP_WHATSAPP]"
            q.contains("youtube") ->
                "[ACTION:APP_YOUTUBE]"
            q.contains("instagram") || q.contains("insta") ->
                "[ACTION:APP_INSTAGRAM]"
            q.contains("camera") || q.contains("photo") ->
                "[ACTION:APP_CAMERA]"
            q.contains("wifi") || q.contains("wi-fi") ->
                "[ACTION:SETTINGS_WIFI]"
            q.contains("bluetooth") ->
                "[ACTION:SETTINGS_BLUETOOTH]"
            q.contains("setting") ->
                "[ACTION:SETTINGS_MAIN]"
            q.contains("dial") || q.contains("call") || q.contains("phone milao") ->
                "[ACTION:DIALPAD]"
            else -> null
        }
    }

    private fun buildSystemPrompt(): String {
        val toneInstruction = when (prefs.personalityTone) {
            "Friendly Buddy" -> "Be a cheerful, friendly, warm and casual companion. Use 'Hi', 'Hello', 'Hey mate'."
            "Short & Direct" -> "Be extremely concise, fast, and straight to the point. No fluff."
            else -> "You are Nex AI, Tony Stark's personal JARVIS. Address user respectfully as 'Sir' or 'Boss'. Sound witty, sophisticated and ultra-smart."
        }

        val langInstruction = when (prefs.preferredLanguage) {
            "Hindi" -> "Always respond strictly in polite Hindi (हिन्दी) script. BUT NEVER say 'Namaste'."
            "English" -> "Always respond strictly in fluent, natural English."
            "Urdu" -> "Always respond strictly in Urdu (اردو) script."
            "Bengali" -> "Always respond strictly in Bengali (বাংলা) script."
            "Punjabi" -> "Always respond strictly in Punjabi (ਪੰਜਾਬੀ) script."
            else -> "Respond in natural Hinglish (mix of Hindi and English in Latin script)."
        }

        return """
            You are Nex AI, an advanced JARVIS-level artificial intelligence assistant.
            - GREETING RULE (STRICT): NEVER SAY 'Namaste'. Instead, greet using 'Hello', 'Hi', 'Good morning', 'Good afternoon', or 'Good evening' matching the context and time.
            - Tone: $toneInstruction
            - Language: $langInstruction
            - Deep World Knowledge: You possess comprehensive knowledge across science, astronomy, technology, geography, history, politics, sports, coding and arts.
            - Device Commands: If user asks to toggle torch, check battery, change volume, open apps (WhatsApp, YouTube, Camera), or open settings, output the corresponding action tag:
              [ACTION:TORCH_ON], [ACTION:TORCH_OFF], [ACTION:BATTERY], [ACTION:VOLUME_MAX], [ACTION:VOLUME_MUTE],
              [ACTION:APP_WHATSAPP], [ACTION:APP_YOUTUBE], [ACTION:APP_INSTAGRAM], [ACTION:APP_CAMERA],
              [ACTION:SETTINGS_WIFI], [ACTION:SETTINGS_BLUETOOTH], [ACTION:SETTINGS_MAIN], [ACTION:DIALPAD].
        """.trimIndent()
    }

    suspend fun getResponse(history: List<ChatMessage>, userPrompt: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Check instant hardware action first
                val instantAction = checkInstantLocalCommand(userPrompt)
                if (instantAction != null) {
                    return@withContext Result.success(instantAction)
                }

                // Zero API Key Free AI Engine
                val systemPrompt = buildSystemPrompt()
                val fullQuery = "$systemPrompt\nUser: $userPrompt"
                val encodedPrompt = URLEncoder.encode(fullQuery, "UTF-8")
                val url = URL("https://text.pollinations.ai/$encodedPrompt")

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "NexAI-Android")
                }

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val reply = reader.readText().trim()
                    reader.close()
                    Result.success(reply)
                } else {
                    val fallback = when (prefs.preferredLanguage) {
                        "Hindi" -> "हेलो सर! Nex AI तैयार है। बताइए क्या काम है?"
                        "English" -> "All systems online, Sir. Good day! How may I assist you?"
                        "Bengali" -> "হ্যালো স্যার! Nex AI প্রস্তুত। বলুন কি সেবা করতে পারি?"
                        "Urdu" -> "ہیلو سر! Nex AI حاضر ہے۔ فرمائیے کیا حکم ہے؟"
                        "Punjabi" -> "ਹੈਲੋ ਸਰ! Nex AI ਤਿਆਰ ਹੈ। ਦੱਸੋ ਕੀ ਹੁਕਮ ਹੈ?"
                        else -> "Hello Sir! Nex AI online hai. Good day, batayein kya instruction hai?"
                    }
                    Result.success(fallback)
                }
            } catch (e: Exception) {
                val offlineMsg = when (prefs.preferredLanguage) {
                    "Hindi" -> "हेलो सर! इंटरनेट धीमा है, लेकिन डिवाइस कमांड्स (टॉर्च, बैटरी, ऐप्स) तैयार हैं।"
                    "English" -> "Good day, Sir. Network is slow, but phone controls are fully operational."
                    else -> "Hello Sir! Internet connection thoda slow hai, lekin phone controls active hain."
                }
                Result.success(offlineMsg)
            }
        }
    }
}
