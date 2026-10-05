package com.ari900630.agentsforlife

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.json.JSONObject

/** Explicit, allow-listed device actions. UI actions run as a continuous user-requested sequence; sensitive device actions still require confirmation. */
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
        if (isDirectUiAction(type)) {
            val result = execute(context, action)
            val description = directDescription(type, action)
            val suffix = if (result) "\n✓ בוצע: $description" else "\n✕ לא ניתן לבצע: $description"
            confirmNext(context, actions, index + 1, text + suffix, onDone)
            return
        }
        val description = when (type) {
            "OPEN_SETTINGS" -> "פתיחת הגדרות: " + action.optString("setting")
            "CALL" -> "פתיחת החייגן למספר: " + action.optString("number")
            "OPEN_URL" -> "פתיחת קישור: " + action.optString("url")
            "LAUNCH_APP" -> "פתיחת אפליקציה: " + action.optString("package")
            "PLAY_STORE_INSTALL" -> "פתיחת חנות Play והתקנת " + action.optString("app", action.optString("package"))
            "UNINSTALL_APP", "UNINSTALL_CURRENT_APP" -> "הסרת האפליקציה " + action.optString("app", "הנוכחית")
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
            "TYPE_TEXT" -> "הקלדת טקסט"
            "SEND_TEXT" -> "הקלדת טקסט ושליחה"
            "CLICK_TEXT" -> "לחיצה על: " + action.optString("text")
            "LONG_CLICK_TEXT" -> "לחיצה ארוכה על: " + action.optString("text")
            "OPEN_CHAT_MENU" -> "פתיחת תפריט שלוש הנקודות בצ'אט"
            "PIN" -> "לחיצה על נעץ"
            "PRESS_SEND" -> "לחיצה על שליחה"
            "LIKE" -> "סימון לייק"
            "FOLLOW" -> "לחיצה על עוקב/עקוב"
            "OPEN_NOTIFICATIONS" -> "פתיחת ההתראות"
            "APPROVE" -> "אישור הפעולה"
            else -> "פעולה במכשיר: $type"
        }

        if (!requiresConfirmation(type)) {
            val result = execute(context, action)
            val suffix = if (result) "\n✓ בוצע: $description" else "\n✕ לא ניתן לבצע: $description"
            confirmNext(context, actions, index + 1, text + suffix, onDone)
            return
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

    private fun isDirectUiAction(type: String): Boolean = type in setOf(
        "TYPE_TEXT", "SEND_TEXT", "CLICK_TEXT", "LONG_CLICK_TEXT", "OPEN_CHAT_MENU",
        "PIN", "PRESS_SEND", "LIKE", "FOLLOW", "OPEN_NOTIFICATIONS", "APPROVE",
        "UNINSTALL_APP", "UNINSTALL_CURRENT_APP"
    )

    private fun directDescription(type: String, action: JSONObject): String = when (type) {
        "UNINSTALL_APP", "UNINSTALL_CURRENT_APP" -> "הסרת האפליקציה " + action.optString("app", "הנוכחית")
        "LIKE" -> "לחיצה על לייק"
        "FOLLOW" -> "מעקב"
        "OPEN_NOTIFICATIONS" -> "פתיחת חלונית ההתראות"
        "APPROVE" -> "אישור הפעולה המוצגת"
        else -> "ביצוע פעולת " + type
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
            "PLAY_STORE_INSTALL" -> AgentAccessibilityService.installFromPlayStore(action.optString("package"), action.optString("app", action.optString("package")))
            "UNINSTALL_APP" -> AgentAccessibilityService.instanceUninstallApp(action.optString("package"), action.optString("app"))
            "UNINSTALL_CURRENT_APP" -> AgentAccessibilityService.instanceUninstallApp(null, null)
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
            "TYPE_TEXT" -> AgentAccessibilityService.typeText(action.optString("text"))
            "SEND_TEXT" -> AgentAccessibilityService.typeText(action.optString("text")) &&
                AgentAccessibilityService.sendCurrentText()
            "CLICK_TEXT" -> AgentAccessibilityService.clickText(action.optString("text").split("|").map { it.trim() }.filter { it.isNotBlank() })
            "LONG_CLICK_TEXT" -> AgentAccessibilityService.longClickText(action.optString("text").split("|").map { it.trim() }.filter { it.isNotBlank() })
            "OPEN_CHAT_MENU" -> AgentAccessibilityService.openChatMenu()
            "PIN" -> AgentAccessibilityService.pinItem()
            "PRESS_SEND" -> AgentAccessibilityService.sendCurrentText()
            "LIKE" -> AgentAccessibilityService.likeCurrentItem()
            "FOLLOW" -> AgentAccessibilityService.followCurrentItem()
            "OPEN_NOTIFICATIONS" -> AgentAccessibilityService.openNotifications()
            "APPROVE" -> AgentAccessibilityService.approveCurrentAction()
            else -> false
        }

    private fun requiresConfirmation(type: String): Boolean {
        return type !in setOf(
            "TYPE_TEXT", "SEND_TEXT", "CLICK_TEXT", "LONG_CLICK_TEXT",
            "OPEN_CHAT_MENU", "PIN", "PRESS_SEND", "LIKE", "FOLLOW", "OPEN_NOTIFICATIONS", "APPROVE"
        )
    }
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
