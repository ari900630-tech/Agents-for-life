package com.ari900630.agentsforlife

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AgentAccessibilityService : AccessibilityService() {
    private val prefs by lazy { getSharedPreferences("agents_settings", MODE_PRIVATE) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        val blocked = prefs.getStringSet("blocked_apps", emptySet()).orEmpty()
        if (pkg in blocked && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        @Volatile private var instance: AgentAccessibilityService? = null

        fun performGlobal(action: Int): Boolean = instance?.performGlobalAction(action) == true
    }
}
