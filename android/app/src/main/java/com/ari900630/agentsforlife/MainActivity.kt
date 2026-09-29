package com.ari900630.agentsforlife

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("agents_settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 15, 30))
            setPadding(28, 28, 28, 28)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        content.addView(text("Agents for Life", 30f, Color.WHITE).apply { gravity = Gravity.CENTER })
        content.addView(text("מרכז שליטה אישי לטלפון", 16f, Color.LTGRAY).apply { gravity = Gravity.CENTER; setPadding(0, 4, 0, 22) })

        status = text("", 17f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.rgb(24, 35, 60))
        }
        content.addView(status, marginParams())

        content.addView(sectionTitle("הרשאות ושירותים"))
        content.addView(actionButton("הפעל / בדוק שירות נגישות") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        content.addView(actionButton("הגדרות סינון שיחות") { openCallSettings() })
        content.addView(actionButton("הרשאת אנשי קשר") { requestContacts() })

        content.addView(sectionTitle("שליטה באפליקציות"))
        content.addView(text("בחר אילו אפליקציות מותר להפעיל. אפליקציה שנחסמה תיסגר כאשר מנסים לפתוח אותה.", 14f, Color.LTGRAY).apply { setPadding(0, 0, 0, 10) })
        val apps = getLaunchableApps()
        if (apps.isEmpty()) content.addView(text("לא נמצאו אפליקציות להפעלה.", 15f, Color.LTGRAY))
        apps.forEach { info ->
            val packageName = info.activityInfo.packageName
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16, 12, 16, 12)
                setBackgroundColor(Color.rgb(18, 27, 48))
            }
            val label = text(info.loadLabel(packageManager).toString(), 16f, Color.WHITE)
            val toggle = Switch(this).apply {
                text = "חסום"
                setTextColor(Color.WHITE)
                isChecked = isAppBlocked(packageName)
                setOnCheckedChangeListener { _, checked -> setAppBlocked(packageName, checked) }
            }
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(toggle)
            content.addView(row, marginParams(0, 8, 0, 0))
        }

        content.addView(sectionTitle("שליטה בשיחות"))
        content.addView(text("מספרים ברשימת החסימה יידחו על ידי שירות סינון השיחות.", 14f, Color.LTGRAY).apply { setPadding(0, 0, 0, 10) })
        val numberInput = EditText(this).apply {
            hint = "הזן מספר לחסימה"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setSingleLine(true)
        }
        content.addView(numberInput, marginParams())
        content.addView(actionButton("חסום מספר") {
            val number = numberInput.text.toString().trim()
            if (number.isNotEmpty()) {
                addBlockedNumber(number)
                numberInput.text.clear()
                Toast.makeText(this, "המספר נוסף לחסימה", Toast.LENGTH_SHORT).show()
                buildScreen()
            }
        })
        val blocked = prefs.getStringSet("blocked_numbers", emptySet()).orEmpty().sorted()
        blocked.forEach { number ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(number, 16f, Color.WHITE), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(actionButton("הסר") { removeBlockedNumber(number); buildScreen() })
            content.addView(row, marginParams())
        }

        content.addView(sectionTitle("הגדרות"))
        content.addView(actionButton("רענן מצב והרשאות") { refreshStatus(); Toast.makeText(this, "המצב עודכן", Toast.LENGTH_SHORT).show() })
        content.addView(actionButton("פתח הגדרות האפליקציה") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) })

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refreshStatus()
    }

    private fun refreshStatus() {
        val accessibility = isAccessibilityServiceEnabled()
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet()).orEmpty().size
        val blockedNumbers = prefs.getStringSet("blocked_numbers", emptySet()).orEmpty().size
        status.text = if (accessibility) {
            "● השירות פעיל\nאפליקציות חסומות: $blockedApps  |  מספרים חסומים: $blockedNumbers"
        } else {
            "○ השירות אינו פעיל\nהפעל את שירות הנגישות כדי לאפשר שליטה באפליקציות"
        }
    }

    private fun getLaunchableApps() = packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        PackageManager.MATCH_ALL
    ).filter { it.activityInfo.packageName != packageName }.distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString() }

    private fun isAppBlocked(pkg: String) = prefs.getStringSet("blocked_apps", emptySet()).orEmpty().contains(pkg)
    private fun setAppBlocked(pkg: String, blocked: Boolean) {
        val set = prefs.getStringSet("blocked_apps", emptySet()).orEmpty().toMutableSet()
        if (blocked) set.add(pkg) else set.remove(pkg)
        prefs.edit().putStringSet("blocked_apps", set).apply()
        refreshStatus()
    }
    private fun addBlockedNumber(number: String) {
        val set = prefs.getStringSet("blocked_numbers", emptySet()).orEmpty().toMutableSet()
        set.add(number)
        prefs.edit().putStringSet("blocked_numbers", set).apply()
    }
    private fun removeBlockedNumber(number: String) {
        val set = prefs.getStringSet("blocked_numbers", emptySet()).orEmpty().toMutableSet()
        set.remove(number)
        prefs.edit().putStringSet("blocked_numbers", set).apply()
    }

    private fun requestContacts() {
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 42)
        } else Toast.makeText(this, "הרשאת אנשי קשר כבר פעילה", Toast.LENGTH_SHORT).show()
    }

    private fun openCallSettings() {
        try { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply { text = value; textSize = size; setTextColor(color) }
    private fun sectionTitle(value: String) = text(value, 21f, Color.WHITE).apply { setPadding(0, 28, 0, 12) }
    private fun actionButton(value: String, action: () -> Unit) = Button(this).apply { text = value; setOnClickListener { action() } }
    private fun marginParams(t: Int = 0, l: Int = 0, b: Int = 10, r: Int = 0) = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(l, t, r, b) }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val expected = ComponentName(this, AgentAccessibilityService::class.java)
        return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            val s = it.resolveInfo.serviceInfo
            ComponentName(s.packageName, s.name) == expected
        }
    }
}
