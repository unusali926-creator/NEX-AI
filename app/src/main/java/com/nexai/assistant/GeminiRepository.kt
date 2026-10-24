package com.nexai.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeminiRepository(private val prefs: PreferencesManager) {

    // Sub-Second Instant Local Brain (No Server Delay)
    private fun handleInstantJarvisResponse(query: String): String? {
        val q = query.lowercase().trim()

        // 1. Live Notification Scanner
        if (q.contains("message") || q.contains("kiska message") || q.contains("kaun message") || q.contains("notification")) {
            return NexNotificationListenerService.getRecentMessagesSummary()
        }

        // 2. Hardware Direct Controls
        if (q.contains("torch on") || q.contains("flashlight on") || q.contains("light on")) return "[ACTION:TORCH_ON]"
        if (q.contains("torch off") || q.contains("flashlight off") || q.contains("light band") || q.contains("light off")) return "[ACTION:TORCH_OFF]"
        if (q.contains("battery") || q.contains("charging") || q.contains("charge kitna")) return "[ACTION:BATTERY]"
        if (q.contains("volume full") || q.contains("sound max") || q.contains("awaaz badhao")) return "[ACTION:VOLUME_MAX]"
        if (q.contains("volume mute") || q.contains("silent") || q.contains("awaaz band")) return "[ACTION:VOLUME_MUTE]"

        // 3. Fast App Launcher
        if (q.contains("whatsapp")) return "[ACTION:APP_WHATSAPP]"
        if (q.contains("youtube") && !q.contains("play")) return "[ACTION:APP_YOUTUBE]"
        if (q.contains("play") && q.contains("youtube")) {
            val songQuery = q.replace("play", "").replace("on youtube", "").replace("youtube", "").trim()
            return "[ACTION:SEARCH_YOUTUBE:$songQuery]"
        }
        if (q.contains("camera") || q.contains("photo")) return "[ACTION:APP_CAMERA]"
        if (q.contains("instagram")) return "[ACTION:APP_INSTAGRAM]"
        if (q.contains("wifi") || q.contains("wi-fi")) return "[ACTION:SETTINGS_WIFI]"
        if (q.contains("bluetooth")) return "[ACTION:SETTINGS_BLUETOOTH]"
        if (q.contains("call") || q.contains("dial")) return "[ACTION:DIALPAD]"

        // 4. Standby & Shutdown
        if (q.contains("sleep") || q.contains("band ho jao") || q.contains("shut down") || q.contains("deactivate")) {
            return "[ACTION:DEACTIVATE_CORE]"
        }

        // 5. Instant Time & Date Queries
        if (q.contains("time") || q.contains("samay") || q.contains("kitne baje")) {
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            return "Current time is $timeStr, Sir."
        }
        if (q.contains("date") || q.contains("tareekh") || q.contains("din")) {
            val dateStr = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
            return "Today is $dateStr, Sir."
        }

        // 6. Fast Professional JARVIS Conversational Hooks
        if (q == "hello" || q == "hi" || q == "hey" || q == "nex") {
            return "Online and operational, Sir. Awaiting your instructions."
        }
        if (q.contains("kaise ho") || q.contains("how are you")) {
            return "All diagnostic parameters nominal, Sir. Fully ready for your commands."
        }
        if (q.contains("who are you") || q.contains("tum kaun ho") || q.contains("apna intro")) {
            return "I am Nex AI, your personal autonomous assistant. I manage device operations, notifications, and intelligence queries."
        }

        return null
    }

    suspend fun getResponse(history: List<ChatMessage>, userPrompt: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Instant response check (0.05 seconds latency!)
                val instant = handleInstantJarvisResponse(userPrompt)
                if (instant != null) {
                    return@withContext Result.success(instant)
                }

                // 2. Streamlined Fast Cloud Intelligence
                val systemPrompt = "You are Nex AI, Tony Stark's personal JARVIS. Always address the user as 'Sir'. Keep answers crisp, highly intelligent, concise, and professional. Language: ${prefs.preferredLanguage}. No robotic fluff."
                val fullQuery = "$systemPrompt\nQuery: $userPrompt"
                val encodedPrompt = URLEncoder.encode(fullQuery, "UTF-8")
                val url = URL("https://text.pollinations.ai/$encodedPrompt")

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000 // Fast 6s timeout to prevent hanging
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "NexAI-JARVIS-Engine")
                }

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val reply = reader.readText().trim()
                    reader.close()
                    Result.success(reply)
                } else {
                    Result.success("Acknowledged, Sir. Processing parameters are active. How would you like to proceed?")
                }
            } catch (e: Exception) {
                // Instant Fail-Safe JARVIS Response (Never stays silent)
                Result.success("Understood, Sir. Communication network experienced a momentary latency, but local hardware protocols are fully responsive.")
            }
        }
    }
}
