package com.nexai.assistant

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                if (prefs.apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("Gemini API Key missing. Please set it in Settings (⚙)."))
                }
                val model = getModel()
                val response = model.generateContent(userPrompt)
                val reply = response.text ?: "I understood your question, but could not produce a response."
                Result.success(reply)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
