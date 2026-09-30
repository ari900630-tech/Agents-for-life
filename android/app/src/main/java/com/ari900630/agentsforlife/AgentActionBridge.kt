package com.ari900630.agentsforlife

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.json.JSONObject

/**
 * Executes the small, explicit action protocol emitted by an agent.
 * Every device action is allow-listed and requires user confirmation.
 */
object AgentActionBridge {
    private const val PREFIX = "[[DEVICE_ACTION:"
    private const val SUFFIX = "]]"

    fun offerActions(context: Context, response: String, onDone: ((String) -> Unit)? = null) {
        val actions = response.split(PREFIX).drop(1).mapNotNull { chunk ->
            val raw = chunk.substringBefore(SUFFIX, "")
            if (raw.isBlank()) null else runCatching { JSONObject(raw) }.getOrNull()
        }
        if (actions.isEmpty()) {
            onDone?.invoke(response)
            return
        }

        val cleaned = response.replace(Regex("\\[\\[DEVICE_ACTION:.*?\\]\\]"), "").trim()
        confirmNext(context, actions, 0, cleaned, onDone)
    }

    private fun confirmNext(context: Context, actions: List<JSONObject>, index: Int, text: String, onDone: ((String) -> Unit)?) {
        if (index >= actions.size) {
            onDone?.invoke(text)
            return
        }
        val action = actions[index]
        val type = action.optString("type")
        val description = when (type) {
            "OPEN_SETTINGS" -> "פתיחת הגדרות: " + action.optString("setting")
            "CALL" -> "פתיחת החייגן למספר: " + action.optString("number")
            "OPEN_URL" -> "פתיחת קישור: " + action.optString("url")
            "LAUNCH_APP" -> "פתיחת אפליקציה: " + action.optString("package")
            "HOME" -> "מעבר למסך הבית"
            "BACK" -> "חזרה"
            "RECENTS" -> "פתיחת האפליקציות האחרונות"
            "NOTIFICATIONS" -> "פתיחת חלונית ההתראות"
            else -> "פעולה במכשיר: $type"
        }

        AlertDialog.Builder(context)
            .setTitle("אישור פעולה")
            .setMessage(description)
            .setNegativeButton("ביטול") { _, _ ->
                confirmNext(context, actions, index + 1, text, onDone)
            }
            .setPositiveButton("אישור") { _, _ ->
                val result = execute(context, action)
                val suffix = if (result) "\n✓ בוצע: $description" else "\n✕ לא ניתן לבצע: $description"
                confirmNext(context, actions, index + 1, text + suffix, onDone)
            }
            .setCancelable(false)
            .show()
    }

    private fun execute(context: Context, action: JSONObject): Boolean {
        return when (action.optString("type")) {
            "OPEN_SETTINGS" -> AgentAction.openSettings(context, action.optString("setting"))
            "CALL" -> AgentAction.call(context, action.optString("number"))
            "OPEN_URL" -> runCatching {
                val url = action.optString("url")
                if (!url.startsWith("https://") && !url.startsWith("http://")) return false
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                true
            }.getOrDefault(false)
            "LAUNCH_APP" -> runCatching {
                val pkg = action.optString("package")
                if (pkg.isBlank()) return false
                val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            "HOME" -> AgentAccessibilityService.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            "BACK" -> AgentAccessibilityService.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            "RECENTS" -> AgentAccessibilityService.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> AgentAccessibilityService.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            else -> false
        }
    }
}
