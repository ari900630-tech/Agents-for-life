package com.ari900630.agentsforlife

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Actions exposed to AI agents only after the user grants the relevant permission. */
object AgentAction {
    fun openSettings(context: Context, action: String): Boolean {
        val intent = when (action) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            else -> return false
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun call(context: Context, phoneNumber: String): Boolean {
        val number = phoneNumber.trim()
        if (number.isEmpty()) return false
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
