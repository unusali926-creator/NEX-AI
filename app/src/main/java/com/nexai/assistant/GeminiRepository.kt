package com.nexai.assistant

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GeminiRepository(private val prefs: PreferencesManager) {

    // 1. Instant Offline Command Matcher (Bina internet delay ke turant action lene ke liye)
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

    private fun getModel(): GenerativeModel {
        val systemPrompt = """
            You are JARVIS (Nex AI), Tony Stark's personal, highly intelligent AI assistant.
            - Address the user respectfully as 'Sir' or 'Boss'.
            - You have comprehensive, up-to-date world knowledge across science, astronomy, technology, history, geography, arts, and general knowledge.
            - Speak fluently and naturally in Hindi, Hinglish, or English based on user query.
            - If the user asks for a device action, output the matching tag:
              [ACTION:TORCH_ON], [ACTION:TORCH_OFF], [ACTION:BATTERY], [ACTION:VOLUME_MAX], [ACTION:VOLUME_MUTE],
              [ACTION:APP_WHATSAPP], [ACTION:APP_YOUTUBE], [ACTION:APP_INSTAGRAM], [ACTION:APP_CAMERA],
              [ACTION:SETTINGS_WIFI], [ACTION:SETTINGS_BLUETOOTH], [ACTION:SETTINGS_MAIN], [ACTION:DIALPAD].
            - Keep your responses confident, witty, and concise like JARVIS.
        """.trimIndent()

        return GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = prefs.apiKey,
            systemInstruction = content { text(systemPrompt) }
        )
    }

    suspend fun getResponse(history: List<ChatMessage>, userPrompt: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // Check if it's an instant hardware command
                val instantAction = checkInstantLocalCommand(userPrompt)
                if (instantAction != null) {
                    return@withContext Result.success(instantAction)
                }

                // If user entered a Gemini key, use official Gemini API
                if (prefs.apiKey.isNotBlank()) {
                    val model = getModel()
                    val response = model.generateContent(userPrompt)
                    val reply = response.text ?: "Systems operational, Sir. Awaiting your instructions."
                    return@withContext Result.success(reply)
                }

                // Zero API Key Free Mode: Pollinations with JARVIS Intelligence
                val systemInstruction = "You are JARVIS, Tony Stark's intelligent AI assistant. Address user as 'Sir'. Provide smart, concise answers in Hindi, Hinglish, or English. You possess full knowledge of science, history, current world and technology."
                val fullQuery = "$systemInstruction\nUser: $userPrompt"
                val encodedPrompt = URLEncoder.encode(fullQuery, "UTF-8")
                val url = URL("https://text.pollinations.ai/$encodedPrompt")

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "NexAI-JARVIS")
                }

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val reply = reader.readText().trim()
                    reader.close()
                    Result.success(reply)
                } else {
                    Result.success("All systems operational, Sir. How may I assist you today?")
                }
            } catch (e: Exception) {
                Result.success("Sir, offline protocols active. Device controls ready, waiting for cloud sync.")
            }
        }
    }
}
