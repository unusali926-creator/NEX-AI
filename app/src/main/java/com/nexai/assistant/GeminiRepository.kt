package com.nexai.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class NexResponse(
    val replyText: String,
    val actionTag: String? = null
)

class GeminiRepository(private val groqApiKey: String = "") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val systemPrompt = """
        You are Nex AI, an advanced JARVIS-class AI assistant created for Tony Stark standards.
        Always address the user politely as 'Sir'. Keep responses concise (1-2 sentences), sharp, witty.
        Languages: English, Hindi, Hinglish, Urdu, Bengali, Punjabi.
        
        CRITICAL HARDWARE ACTIONS:
        If user wants hardware actions, append an exact tag at the very end of your response:
        - Turn on flashlight: [ACTION:FLASHLIGHT_ON]
        - Turn off flashlight: [ACTION:FLASHLIGHT_OFF]
        - Check battery: [ACTION:BATTERY]
        - Max volume: [ACTION:VOLUME_MAX]
        - Mute volume: [ACTION:VOLUME_MUTE]
        - Open WhatsApp: [ACTION:OPEN_WHATSAPP]
        - Open YouTube: [ACTION:OPEN_YOUTUBE]
        - Open Camera: [ACTION:OPEN_CAMERA]
    """.trimIndent()

    suspend fun getResponse(userMessage: String): NexResponse = withContext(Dispatchers.IO) {
        // 1. Try Groq (Fastest) if key exists
        if (groqApiKey.isNotBlank() && groqApiKey != "APNI_KEY_YAHAN_DALO") {
            try {
                val groqReply = callGroqApi(userMessage)
                if (groqReply != null) return@withContext parseActionTag(groqReply)
            } catch (e: Exception) {
                // Fallback to Pollinations
            }
        }

        // 2. Fallback: Free Zero-Key Pollinations Engine
        return@withContext callPollinations(userMessage)
    }

    private fun callGroqApi(message: String): String? {
        val url = "https://api.groq.com/openai/v1/chat/completions"
        val jsonPayload = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
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
            put("max_tokens", 120)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $groqApiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        }
        return null
    }

    private fun callPollinations(message: String): NexResponse {
        return try {
            val fullPrompt = "$systemPrompt\nUser: $message"
            val encodedPrompt = URLEncoder.encode(fullPrompt, "UTF-8")
            val url = "https://text.pollinations.ai/$encodedPrompt"

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: "Online and ready, Sir."
                parseActionTag(body)
            }
        } catch (e: Exception) {
            NexResponse("Sir, I am having trouble connecting to satellite feeds.", null)
        }
    }

    private fun parseActionTag(rawText: String): NexResponse {
        val regex = "\\[ACTION:[A-Z_]+\\]".toRegex()
        val match = regex.find(rawText)
        val actionTag = match?.value
        val cleanReply = rawText.replace(regex, "").trim()
        return NexResponse(cleanReply, actionTag)
    }
}
