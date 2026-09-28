package com.ari900630.agentsforlife

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        val title = TextView(this).apply { text = "Agents for Life"; textSize = 28f; gravity = Gravity.CENTER }
        val status = TextView(this).apply { text = "הפעלת שירותי הטלפון"; textSize = 18f; gravity = Gravity.CENTER; setPadding(0,24,0,24) }
        val accessibility = TextView(this).apply {
            text = "שליטה בטלפון / הרשאות"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(20,20,20,20)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(title)
        root.addView(status)
        root.addView(accessibility)
        setContentView(root)
    }
}
