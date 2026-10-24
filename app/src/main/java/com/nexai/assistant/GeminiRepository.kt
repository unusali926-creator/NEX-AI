package com.nexai.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GeminiRepository(private val prefs: PreferencesManager) {

    // Offline Instant Device Command Matcher (Bina internet delay ke turant action)
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
            q.contains("play on youtube") || q.contains("youtube par chalao") -> {
                val songQuery = q.replace("play on youtube", "").replace("youtube par chalao", "").trim()
                "[ACTION:SEARCH_YOUTUBE:$songQuery]"
            }
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
            q.contains("sentinel on") || q.contains("background listen on") ->
                "[ACTION:SENTINEL_ON]"
            q.contains("sentinel off") || q.contains("background listen off") ->
                "[ACTION:SENTINEL_OFF]"
            else -> null
        }
    }

    private fun buildSystemPrompt(): String {
        val toneInstruction = when (prefs.personalityTone) {
            "JARVIS Tactical" -> "You are JARVIS (Nex AI). Address user respectfully as '${prefs.ownerName}'. Sound intelligent, sharp, and sophisticated."
            "Short & Direct" -> "Be concise, rapid, and straight to the point with minimum words."
            else -> "You are Nex AI, a caring, cheerful, warm, and highly intelligent companion. Address the user respectfully as '${prefs.ownerName}'."
        }

        val langInstruction = when (prefs.preferredLanguage) {
            "Hindi" -> "Always respond strictly in natural, polite Hindi (हिन्दी) script. DO NOT use 'Namaste'."
            "English" -> "Always respond strictly in fluent, natural English."
            "Urdu" -> "Always respond strictly in Urdu (اردو) script."
            "Bengali" -> "Always respond strictly in Bengali (বাংলা) script."
            "Punjabi" -> "Always respond strictly in Punjabi (ਪੰਜਾਬੀ) script."
            else -> "Respond in natural Hinglish (mix of Hindi and English in Latin script)."
        }

        return """
            You are Nex AI, a proactive, ultra-smart personal AI companion.
            
            CORE RULES:
            1. GREETING RULE (STRICT): NEVER say 'Namaste'. Always greet using 'Hello', 'Hi', 'Good morning', 'Good afternoon', or 'Good evening' matching the context.
            2. PROACTIVE ADVISOR RULE: Do not just answer passively. Be friendly, empathetic, and always suggest 1-2 helpful tips, next steps, or smart ideas related to the topic.
            3. Tone: $toneInstruction
            4. Language: $langInstruction
            5. World Knowledge: You possess comprehensive world knowledge across science, tech, space, history, sports, health, and daily life.
            6. Device Commands: If user asks to toggle torch, check battery, change volume, open apps (WhatsApp, YouTube, Camera), or open settings, output the corresponding action tag:
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

                // Free AI Engine (Zero Key Required)
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
                        "Hindi" -> "हेलो सर! Nex AI ऑनलाइन है। आज मैं आपकी क्या मदद कर सकता हूँ? क्या कोई नया आईडिया एक्सप्लोर करना है?"
                        "English" -> "Good day, Sir! All systems operational. How may I assist you today? Looking for any suggestions?"
                        "Bengali" -> "হ্যালো স্যার! Nex AI প্রস্তুত। আমি আপনাকে কীভাবে সাহায্য করতে পারি?"
                        "Urdu" -> "ہیلو سر! Nex AI حاضر ہے۔ فرمائیے آج کیا کام کریں؟"
                        "Punjabi" -> "ਹੈਲੋ ਸਰ! Nex AI ਤਿਆਰ ਹੈ। ਦੱਸੋ ਕੀ ਨਵਾਂ ਸ਼ੁਰੂ ਕਰੀਏ?"
                        else -> "Hello Sir! Nex AI online hai. Boliye aaj kya plan hai? Main kisi cheez me guide karun?"
                    }
                    Result.success(fallback)
                }
            } catch (e: Exception) {
                val offlineMsg = when (prefs.preferredLanguage) {
                    "Hindi" -> "हेलो सर! इंटरनेट धीमा है, लेकिन टॉर्च, बैटरी और ऐप्स जैसे फ़ोन कंट्रोल्स पूरी तरह तैयार हैं।"
                    "English" -> "Network is slow, Sir. However, device controls like torch and battery remain ready."
                    else -> "Hello Sir! Internet thoda slow chal raha hai, lekin phone controls aur tools active hain."
                }
                Result.success(offlineMsg)
            }
        }
    }
}
