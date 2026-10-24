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

    private fun getModel(): GenerativeModel {
        val systemPrompt = """
            You are Nex, a friendly, warm, and helpful AI companion.
            - You communicate naturally in Hindi, Hinglish, and English based on the user's preference or input language.
            - Keep your responses conversational, clean, and formatted nicely.
            - Honesty rule: You are a chat-based assistant and cannot click buttons or alter device hardware directly. Do not pretend to control phone settings or apps if you have not actually performed the action.
            - Current user language preference: ${prefs.preferredLanguage}.
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
                // 1. Agar user ne Settings me Gemini Key daali hai to Gemini use karo
                if (prefs.apiKey.isNotBlank()) {
                    val model = getModel()
                    val response = model.generateContent(userPrompt)
                    val reply = response.text ?: "Kuch samajh nahi aaya, kripya dobara puchiye."
                    return@withContext Result.success(reply)
                }

                // 2. Agar koi API Key NAHI hai, to Free Open-AI Engine use karo (Zero Key Required)
                val systemPrompt = "You are Nex AI, a helpful Indian voice assistant. Answer in Hindi, Hinglish, or English. Be polite, concise, and smart. Preference: ${prefs.preferredLanguage}."
                val fullQuery = "$systemPrompt\nUser: $userPrompt"
                val encodedPrompt = URLEncoder.encode(fullQuery, "UTF-8")
                val url = URL("https://text.pollinations.ai/$encodedPrompt")

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "NexAI-App")
                }

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val reply = reader.readText().trim()
                    reader.close()
                    Result.success(reply)
                } else {
                    Result.success("Namaste! Main Nex AI hoon. Aap mujhse Hindi ya English me koi bhi sawal pooch sakte hain.")
                }
            } catch (e: Exception) {
                // Network slow hone par friendly fallback response
                Result.success("Namaste! Internet connection thoda slow hai, kripya dobara boliye ya type kijiye.")
            }
        }
    }
}
