package com.ari900630.agentsforlife

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.json.JSONObject

/** Explicit, allow-listed device actions. Every action requires user confirmation. */
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
        if (index >= actions.size) { onDone?.invoke(text); return }
        val action = actions[index]
        val type = action.optString("type")
        val description = when (type) {
            "OPEN_SETTINGS" -> "פתיחת הגדרות: " + action.optString("setting")
            "CALL" -> "פתיחת החייגן למספר: " + action.optString("number")
            "OPEN_URL" -> "פתיחת קישור: " + action.optString("url")
            "LAUNCH_APP" -> "פתיחת אפליקציה: " + action.optString("package")
            "PLAY_STORE_INSTALL" -> "פתיחת חנות Play והתקנת " + action.optString("app", action.optString("package"))
            "HOME" -> "מעבר למסך הבית"
            "BACK" -> "חזרה"
            "RECENTS" -> "פתיחת האפליקציות האחרונות"
            "NOTIFICATIONS" -> "פתיחת חלונית ההתראות"
            "QUICK_SETTINGS" -> "פתיחת ההגדרות המהירות"
            "POWER_DIALOG" -> "פתיחת תפריט הכיבוי"
            "LOCK_SCREEN" -> "נעילת המסך"
            "SCREENSHOT" -> "צילום מסך"
            "SHARE_TEXT" -> "פתיחת חלון שיתוף"
            "SMS" -> "פתיחת הודעת SMS מוכנה לשליחה"
            "EMAIL" -> "פתיחת הודעת דואר מוכנה לשליחה"
            "MAP" -> "פתיחת מפה"
            else -> "פעולה במכשיר: $type"
        }

        AlertDialog.Builder(context)
            .setTitle("אישור פעולה")
            .setMessage(description)
            .setNegativeButton("ביטול") { _, _ -> confirmNext(context, actions, index + 1, text, onDone) }
            .setPositiveButton("אישור") { _, _ ->
                val result = execute(context, action)
                val suffix = if (result) "\n✓ בוצע: $description" else "\n✕ לא ניתן לבצע: $description"
                confirmNext(context, actions, index + 1, text + suffix, onDone)
            }
            .setCancelable(false)
            .show()
    }

    private fun execute(context: Context, action: JSONObject): Boolean {
        val type = action.optString("type")
        val started = System.currentTimeMillis()
        var result = false
        var reason = "unknown"
        result = when (type) {
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
            "PLAY_STORE_INSTALL" -> AgentAccessibilityService.installFromPlayStore(action.optString("package"), action.optString("app", action.optString("package")))
            "HOME" -> AgentAccessibilityService.performGlobal(AccessibilityServiceAction.HOME)
            "BACK" -> AgentAccessibilityService.performGlobal(AccessibilityServiceAction.BACK)
            "RECENTS" -> AgentAccessibilityService.performGlobal(AccessibilityServiceAction.RECENTS)
            "NOTIFICATIONS" -> AgentAccessibilityService.performGlobal(AccessibilityServiceAction.NOTIFICATIONS)
            "QUICK_SETTINGS" -> AgentAccessibilityService.performGlobal(AccessibilityServiceAction.QUICK_SETTINGS)
            "POWER_DIALOG" -> AgentAccessibilityService.performGlobal(AccessibilityServiceAction.POWER_DIALOG)
            "LOCK_SCREEN" -> AgentAccessibilityService.performGlobal(AccessibilityServiceAction.LOCK_SCREEN)
            "SCREENSHOT" -> AgentAccessibilityService.performGlobal(AccessibilityServiceAction.SCREENSHOT)
            "SHARE_TEXT" -> runCatching {
                val text = action.optString("text")
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                }, "שיתוף"))
                true
            }.getOrDefault(false)
            "SMS" -> runCatching {
                val number = action.optString("number")
                val body = action.optString("body")
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).apply {
                    putExtra("sms_body", body)
                })
                true
            }.getOrDefault(false)
            "EMAIL" -> runCatching {
                val to = action.optString("to")
                val subject = action.optString("subject")
                val body = action.optString("body")
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to))).apply {
                    putExtra(Intent.EXTRA_SUBJECT, subject); putExtra(Intent.EXTRA_TEXT, body)
                })
                true
            }.getOrDefault(false)
            "MAP" -> runCatching {
                val query = action.optString("query")
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))))
                true
            }.getOrDefault(false)
            else -> false
        }
        reason = if (result) "action reported success" else "action returned failure or target unavailable"
        AgentAccessibilityService.recordActionDiagnostic(type, result, reason, System.currentTimeMillis() - started)
        return result
    }

    private object AccessibilityServiceAction {
        const val HOME = 2
        const val BACK = 1
        const val RECENTS = 3
        const val NOTIFICATIONS = 4
        const val POWER_DIALOG = 6
        const val QUICK_SETTINGS = 5
        const val LOCK_SCREEN = 8
        const val SCREENSHOT = 9
    }
}
