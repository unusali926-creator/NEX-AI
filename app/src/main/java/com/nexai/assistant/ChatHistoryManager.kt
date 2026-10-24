package com.nexai.assistant

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Chat",
    val timestamp: Long = System.currentTimeMillis(),
    val messages: MutableList<ChatMessage> = mutableListOf()
)

class ChatHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nex_ai_chat_sessions", Context.MODE_PRIVATE)

    fun getActiveSessionId(): String {
        return prefs.getString("active_session_id", "") ?: ""
    }

    fun setActiveSessionId(sessionId: String) {
        prefs.edit().putString("active_session_id", sessionId).apply()
    }

    fun createNewSession(initialGreeting: String): ChatSession {
        val newSession = ChatSession(
            title = "Chat ${System.currentTimeMillis() % 10000}",
            messages = mutableListOf(
                ChatMessage(
                    sender = Sender.NEX,
                    text = initialGreeting
                )
            )
        )
        saveSession(newSession)
        setActiveSessionId(newSession.id)
        return newSession
    }

    fun getAllSessions(): List<ChatSession> {
        val sessionsJsonStr = prefs.getString("all_sessions_list", "[]") ?: "[]"
        val list = mutableListOf<ChatSession>()
        try {
            val jsonArray = JSONArray(sessionsJsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val timestamp = obj.getLong("timestamp")

                val messages = loadSessionMessages(id)
                list.add(ChatSession(id = id, title = title, timestamp = timestamp, messages = messages))
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun saveSession(session: ChatSession) {
        // 1. Save messages of this session
        val msgArray = JSONArray()
        for (msg in session.messages) {
            val obj = JSONObject().apply {
                put("id", msg.id)
                put("sender", msg.sender.name)
                put("text", msg.text)
                put("timestamp", msg.timestamp)
            }
            msgArray.put(obj)
        }
        prefs.edit().putString("session_msgs_${session.id}", msgArray.toString()).apply()

        // 2. Update session metadata in all sessions list
        val sessions = getAllSessions().toMutableList()
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index != -1) {
            sessions[index] = session
        } else {
            sessions.add(0, session)
        }

        val allArray = JSONArray()
        for (s in sessions) {
            val sObj = JSONObject().apply {
                put("id", s.id)
                put("title", s.title)
                put("timestamp", s.timestamp)
            }
            allArray.put(sObj)
        }
        prefs.edit().putString("all_sessions_list", allArray.toString()).apply()
    }

    private fun loadSessionMessages(sessionId: String): MutableList<ChatMessage> {
        val msgsJson = prefs.getString("session_msgs_$sessionId", "[]") ?: "[]"
        val list = mutableListOf<ChatMessage>()
        try {
            val arr = JSONArray(msgsJson)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    ChatMessage(
                        id = o.getString("id"),
                        sender = Sender.valueOf(o.getString("sender")),
                        text = o.getString("text"),
                        timestamp = o.getLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore
        }
        return list
    }

    fun deleteSession(sessionId: String) {
        prefs.edit().remove("session_msgs_$sessionId").apply()
        val sessions = getAllSessions().filter { it.id != sessionId }

        val allArray = JSONArray()
        for (s in sessions) {
            val sObj = JSONObject().apply {
                put("id", s.id)
                put("title", s.title)
                put("timestamp", s.timestamp)
            }
            allArray.put(sObj)
        }
        prefs.edit().putString("all_sessions_list", allArray.toString()).apply()
    }
}
