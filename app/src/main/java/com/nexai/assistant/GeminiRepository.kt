package com.nexai.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GeminiRepository(
    private val prefs: PreferencesManager? = null,
    private val groqApiKey: String = "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"
) {

    private val systemPrompt = """
        You are J.A.R.V.I.S. (Nex AI), Tony Stark's personal hyper-intelligent AI assistant.
        Always address the user politely as 'Sir'. 
        Give extremely concise, crisp, and direct replies (maximum 1 short sentence). 
        Sound calm, sophisticated, and British.
        
        CRITICAL HARDWARE TAGS:
        - Turn off/Sleep mode/Band ho jao/Go to sleep: [ACTION:SLEEP_MODE]
        - Read incoming message / Kaun message kiya: [ACTION:READ_NOTIFICATION]
        - Current time / kitne baje hain: [ACTION:TIME]
        - Turn on flashlight: [ACTION:FLASHLIGHT_ON]
        - Turn off flashlight: [ACTION:FLASHLIGHT_OFF]
        - Check battery: [ACTION:BATTERY]
        - Open WhatsApp: [ACTION:OPEN_WHATSAPP]
        - Open YouTube: [ACTION:OPEN_YOUTUBE]
        - Open Camera: [ACTION:OPEN_CAMERA]
    """.trimIndent()

    suspend fun getResponse(
        chatHistory: List<ChatMessage>,
        userMessage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val activeKey = groqApiKey.trim()

            if (activeKey.isNotBlank()) {
                val groqReply = callGroq(activeKey, userMessage)
                if (groqReply != null) return@withContext Result.success(groqReply)
            }

            val pollReply = callPollinations(userMessage)
            Result.success(pollReply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getResponse(userMessage: String): Result<String> {
        return getResponse(emptyList(), userMessage)
    }

    private fun callGroq(apiKey: String, message: String): String? {
        return try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true

            // llama-3.1-8b-instant gives 200ms ultra-fast replies!
            val jsonPayload = JSONObject().apply {
                put("model", "llama-3.1-8b-instant")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", message)
                    })
                })
                put("max_tokens", 80)
                put("temperature", 0.5)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonPayload.toString())
                writer.flush()
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun callPollinations(message: String): String {
        return try {
            val fullPrompt = "$systemPrompt\nUser: $message"
            val encodedPrompt = URLEncoder.encode(fullPrompt, "UTF-8")
            val url = URL("https://text.pollinations.ai/$encodedPrompt")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                "Online and standing by, Sir."
            }
        } catch (e: Exception) {
            "Sir, satellite link is weak."
        }
    }
}
