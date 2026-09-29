package com.ari900630.agentsforlife

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LocalAgent(
    val id: String,
    val name: String,
    val type: String,
    val instructions: String
)

object AgentStore {
    private const val PREFS = "agents_store"
    private const val KEY = "agents"

    val templates = listOf(
        "עוזר אישי" to "עזור לי לנהל משימות והגדרות בטלפון.",
        "חוקר" to "חקור נושא, סכם מקורות והצג מידע ברור.",
        "כותב" to "כתוב וערוך טקסטים לפי ההוראות שלי.",
        "מתכנת" to "עזור לי לכתוב, לבדוק ולתקן קוד.",
        "מתכנן" to "בנה תוכניות עבודה ושלבים לביצוע.",
        "שיווק" to "עזור בתוכן, שיווק וקמפיינים.",
        "רשתות חברתיות" to "צור תוכן ורעיונות לרשתות חברתיות.",
        "מורה" to "למד אותי נושאים בצורה מדורגת.",
        "מתרגם" to "תרגם ושפר טקסטים בין שפות.",
        "מנתח נתונים" to "נתח נתונים והפק תובנות."
    )

    fun load(context: Context): MutableList<LocalAgent> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return mutableListOf()
        val array = JSONArray(raw)
        return MutableList(array.length()) { i ->
            val o = array.getJSONObject(i)
            LocalAgent(o.getString("id"), o.getString("name"), o.getString("type"), o.getString("instructions"))
        }
    }

    fun save(context: Context, agents: List<LocalAgent>) {
        val array = JSONArray()
        agents.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("type", it.type)
                put("instructions", it.instructions)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }

    fun create(context: Context, name: String, type: String, instructions: String): LocalAgent {
        val agent = LocalAgent(
            System.currentTimeMillis().toString(),
            name.ifBlank { type },
            type,
            instructions
        )
        val all = load(context)
        all.add(agent)
        save(context, all)
        return agent
    }

    fun seedTemplates(context: Context) {
        if (load(context).isNotEmpty()) return
        val agents = templates.mapIndexed { i, pair ->
            LocalAgent("template-$i", pair.first, pair.first, pair.second)
        }
        save(context, agents)
    }
}
