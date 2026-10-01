package com.ari900630.agentsforlife

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(val role: String, val text: String, val time: Long = System.currentTimeMillis())
data class ChatSession(val id: String, var title: String, val messages: MutableList<ChatMessage>, var updatedAt: Long = System.currentTimeMillis())

object ChatStore {
    private const val PREFS = "chat_store"
    private const val KEY = "sessions"

    fun load(context: Context): MutableList<ChatSession> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return mutableListOf()
        return try {
            val a = JSONArray(raw)
            MutableList(a.length()) { i ->
                val o = a.getJSONObject(i)
                val m = o.optJSONArray("messages") ?: JSONArray()
                val messages = MutableList(m.length()) { j ->
                    val x = m.getJSONObject(j)
                    ChatMessage(x.optString("role"), x.optString("text"), x.optLong("time", System.currentTimeMillis()))
                }
                ChatSession(o.getString("id"), o.optString("title", "שיחה חדשה"), messages, o.optLong("updatedAt", System.currentTimeMillis()))
            }.sortedByDescending { it.updatedAt }.toMutableList()
        } catch (_: Exception) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
            mutableListOf()
        }
    }

    fun save(context: Context, sessions: List<ChatSession>) {
        val a = JSONArray()
        sessions.forEach { s ->
            val o = JSONObject().apply { put("id", s.id); put("title", s.title); put("updatedAt", s.updatedAt) }
            val m = JSONArray()
            s.messages.forEach { msg -> m.put(JSONObject().apply { put("role", msg.role); put("text", msg.text); put("time", msg.time) }) }
            o.put("messages", m); a.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply()
    }

    fun create(context: Context, title: String = "שיחה חדשה"): ChatSession {
        val s = ChatSession(System.currentTimeMillis().toString(), title, mutableListOf())
        val all = load(context); all.add(0, s); save(context, all); return s
    }
    fun find(context: Context, id: String): ChatSession? = load(context).firstOrNull { it.id == id }
    fun addMessage(context: Context, sessionId: String, role: String, text: String) {
        val all = load(context); val s = all.firstOrNull { it.id == sessionId } ?: return
        s.messages.add(ChatMessage(role, text)); s.updatedAt = System.currentTimeMillis()
        if (role == "user" && (s.title == "שיחה חדשה" || s.title.isBlank())) s.title = text.trim().replace("\\s+".toRegex(), " ").take(42).ifBlank { "שיחה חדשה" }
        save(context, all)
    }
    fun delete(context: Context, id: String) = save(context, load(context).filterNot { it.id == id })
}