package com.ari900630.agentsforlife

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.view.Window
import android.view.WindowManager

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var accessibility: TextView
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityState()
    }

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Agents for Life"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        status = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        accessibility = TextView(this).apply {
            text = "הפעלת השירות"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(40, 24, 40, 24)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        root.addView(title)
        root.addView(status)
        root.addView(accessibility)
        setContentView(root)
    }

    private fun updateAccessibilityState() {
        val enabled = isAccessibilityServiceEnabled()
        if (enabled) {
            status.text = "השירות הופעל בהצלחה"
            accessibility.text = "✓ השירות פעיל"
            accessibility.isEnabled = false
            accessibility.alpha = 0.6f
        } else {
            status.text = "יש להפעיל את השירות כדי להמשיך"
            accessibility.text = "הפעלת השירות"
            accessibility.isEnabled = true
            accessibility.alpha = 1f
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val expected = ComponentName(this, AgentAccessibilityService::class.java)
        return enabledServices.any { info ->
            info.resolveInfo.serviceInfo.let {
                ComponentName(it.packageName, it.name) == expected
            }
        }
    }
}
