package com.ari900630.agentsforlife

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.*
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.content.pm.PackageManager
import java.util.Locale
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("agents_settings", MODE_PRIVATE) }
    private val defaultServerUrl = "https://agents-for-life.onrender.com"

    private val bg = Color.rgb(249, 249, 253)
    private val surface = Color.WHITE
    private val primary = Color.rgb(107, 106, 211)
    private val secondary = Color.rgb(151, 160, 239)
    private val ink = Color.rgb(35, 31, 42)
    private val muted = Color.rgb(118, 116, 130)
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("he", "IL") }
        showHome()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 16)
        }
        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(10, 8, 10, 8)
            background = rounded(surface, 26f, Color.rgb(232, 231, 241))
        }
        nav.addView(navButton("בית") { showHome() }, weightParams())
        nav.addView(navButton("סוכנים") { showAgentsDialog() }, weightParams())
        nav.addView(navButton("צ׳אט") { showChat() }, weightParams())
        root.addView(nav, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun showHome() {
        content.removeAllViews()
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = text("Agents for Life", 23f, ink).apply { setTypeface(null, Typeface.BOLD) }
        top.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(iconButton("⚙") { showServerInfo() }, LinearLayout.LayoutParams(52, 52))
        content.addView(top, layoutParams(0, 0, 8, 0))

        content.addView(text("שיחה חכמה עם הסוכן שלך", 15f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        })

        content.addView(createOrb("מוכן לשיחה", false), layoutParams(0, 0, 10, 0))
        content.addView(text("מה תרצה שאעשה עבורך?", 22f, ink).apply {
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 4, 0, 6)
        })
        content.addView(text("כתוב משימה או עבור לצ׳אט כדי להתחיל.", 14f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })

        val start = primaryButton("התחל שיחה") { showChat() }
        content.addView(start, layoutParams(0, 12, 0, 12))

        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quick.addView(smallCard("סוכנים", "ניהול הסוכנים") { showAgentsDialog() }, weightParams())
        quick.addView(Space(this), LinearLayout.LayoutParams(10, 1))
        quick.addView(smallCard("שרת", "מצב החיבור") { showServerInfo() }, weightParams())
        content.addView(quick, layoutParams(0, 8, 0, 0))

        content.addView(section("מצב המערכת"))
        status = text("", 14f, ink).apply {
            gravity = Gravity.CENTER
            setPadding(16, 15, 16, 15)
            background = rounded(surface, 18f, Color.rgb(229, 228, 239))
        }
        content.addView(status)
        refreshStatus()
    }

    private fun showChat() {
        content.removeAllViews()

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(text("שיחה חדשה", 27f, ink).apply { setTypeface(null, Typeface.BOLD) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(iconButton("＋") { showHome() }, LinearLayout.LayoutParams(52, 52))
        content.addView(top, layoutParams(0, 0, 12, 0))

        val orbBox = createOrb("מוכן להקשיב", true)
        content.addView(orbBox, layoutParams(0, 0, 8, 0))

        val mode = text("AUTO  •  GROQ  •  GPT-OSS-20B", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        content.addView(mode)

        val task = EditText(this).apply {
            hint = "מה אתה רוצה שהסוכן יעשה?"
            setHintTextColor(Color.rgb(160, 158, 170))
            setTextColor(ink)
            textSize = 16f
            minLines = 5
            gravity = Gravity.TOP or Gravity.RIGHT
            background = rounded(surface, 20f, Color.rgb(230, 229, 238))
            setPadding(18, 16, 18, 16)
        }
        content.addView(task, layoutParams(0, 0, 10, 0))

        val result = text("התשובה של הסוכן תופיע כאן.", 15f, ink).apply {
            setPadding(18, 17, 18, 17)
            background = rounded(surface, 20f, Color.rgb(230, 229, 238))
        }
        content.addView(result, layoutParams(0, 0, 10, 0))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(primaryButton("שלח לסוכן") {
            runAgent(task, result, orbBox)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Space(this), LinearLayout.LayoutParams(10, 1))
        actions.addView(iconButton("🎙") {
            startVoiceInput(task, result, orbBox)
        }, LinearLayout.LayoutParams(58, 58))
        content.addView(actions)
    }

    private fun runAgent(task: EditText, result: TextView, orb: View) {
        val request = task.text.toString().trim()
        val agents = AgentStore.load(this).ifEmpty { AgentStore.seedTemplates(this); AgentStore.load(this) }
        val a = agents.firstOrNull { it.type == "assistant" } ?: agents.firstOrNull()
        val server = prefs.getString("agent_server", defaultServerUrl).orEmpty()
        if (request.isEmpty()) { result.text = "כתוב משימה."; return }
        if (a == null) { result.text = "לא נמצא סוכן."; return }
        if (server.isBlank()) { result.text = "שרת ה-AI עדיין לא מוגדר באפליקציה."; return }

        result.text = "הסוכן חושב…"
        orb.alpha = 0.72f
        AgentAccessibilityService.startLivePreview()
        Thread {
            val r = AgentApiClient.run(server, a.name, a.instructions, request, "Server", "", "openai/gpt-oss-20b", "Groq")
            runOnUiThread {
                orb.alpha = 1f
                r.fold(
                    { answer -> AgentActionBridge.offerActions(this, answer) { result.text = it; speak(it); AgentAccessibilityService.stopLivePreview() } },
                    { e -> result.text = "שגיאה: " + (e.message ?: "לא ידועה"); speak(result.text.toString()); AgentAccessibilityService.stopLivePreview() }
                )
            }
        }.start()
    }


    private fun startVoiceInput(task: EditText, result: TextView, orb: View) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            result.text = "זיהוי דיבור אינו זמין במכשיר."
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 701)
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { result.text = "מקשיב…"; orb.alpha = 1f }
            override fun onBeginningOfSpeech() { result.text = "מקשיב לך…" }
            override fun onRmsChanged(rmsdB: Float) { orb.scaleX = 1f + (rmsdB.coerceIn(0f, 10f) / 35f); orb.scaleY = orb.scaleX }
            override fun onEndOfSpeech() { orb.scaleX = 1f; orb.scaleY = 1f; result.text = "מעבד את הבקשה…" }
            override fun onError(error: Int) { orb.scaleX = 1f; orb.scaleY = 1f; result.text = "לא הצלחתי להבין. נסה שוב." }
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                task.setText(heard)
                if (heard.isNotBlank()) runAgent(task, result, orb)
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun speak(text: String) {
        val clean = text.replace(Regex("\\[\\[DEVICE_ACTION:.*?\\]\\]"), "").trim()
        if (clean.isNotBlank()) tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "agent-answer")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 701 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "המיקרופון אושר. לחץ שוב על המיקרופון כדי לדבר.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        AgentAccessibilityService.stopLivePreview()
        super.onDestroy()
    }

    private fun showServerInfo() {
        content.removeAllViews()
        content.addView(title("חיבור מערכת", 27f))
        content.addView(subtitle("המשתמש לא צריך להגדיר מפתח API או לבחור מודל.", 15f))

        val endpoint = EditText(this).apply {
            hint = "כתובת שרת AI"
            setHintTextColor(Color.rgb(160, 158, 170))
            setTextColor(ink)
            setSingleLine()
            setText(prefs.getString("agent_server", defaultServerUrl))
            background = rounded(surface, 16f, Color.rgb(230, 229, 238))
            setPadding(16, 12, 16, 12)
        }
        content.addView(endpoint, layoutParams(0, 0, 10, 0))

        content.addView(primaryButton("שמור כתובת שרת") {
            prefs.edit().putString("agent_server", endpoint.text.toString().trim())
                .putString("ai_provider", "Server").remove("ai_key").apply()
            Toast.makeText(this, "נשמר — אין צורך במפתח API", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }, layoutParams(0, 0, 10, 0))

        content.addView(cardButton("הפעל שליטה במכשיר", "נדרש אישור חד-פעמי בהגדרות נגישות Android") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
    }

    private fun showAgentsDialog() {
        AgentStore.seedTemplates(this)
        val agents = AgentStore.load(this)
        val names = agents.map { it.name + " — " + it.type }.toMutableList()
        names.add(0, "+ צור סוכן חדש")
        AlertDialog.Builder(this).setTitle("הסוכנים שלי").setItems(names.toTypedArray()) { _, which ->
            if (which == 0) showCreateAgentDialog()
            else Toast.makeText(this, "הסוכן " + agents[which - 1].name + " נבחר", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("סגור", null).show()
    }

    private fun showCreateAgentDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 8, 30, 0) }
        val name = EditText(this).apply { hint = "שם הסוכן" }
        val type = EditText(this).apply { hint = "סוג הסוכן" }
        val ins = EditText(this).apply { hint = "מה הסוכן צריך לעשות?"; minLines = 4 }
        box.addView(name); box.addView(type); box.addView(ins)
        AlertDialog.Builder(this).setTitle("יצירת סוכן חדש").setView(box)
            .setNegativeButton("ביטול", null)
            .setPositiveButton("צור") { _, _ ->
                AgentStore.create(this, name.text.toString(), type.text.toString(), ins.text.toString())
                Toast.makeText(this, "הסוכן נוצר", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun refreshStatus() {
        val server = prefs.getString("agent_server", defaultServerUrl).orEmpty()
        if (!::status.isInitialized) return
        status.text = if (server.isBlank()) "○ שרת AI לא מוגדר\nהיכנס לחיבור מערכת והוסף כתובת שרת"
        else "● שרת AI מוגדר\nהחיבור מוכן לשימוש"
    }

    private fun createOrb(label: String, active: Boolean): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 4)
        }
        val orb = TextView(this).apply {
            text = "✦"
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(214, 214, 246),
                    Color.rgb(151, 160, 239),
                    Color.rgb(107, 106, 211),
                    Color.rgb(86, 63, 60)
                )
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(2, Color.rgb(214, 214, 246))
            }
            elevation = 10f
        }
        val size = (if (active) 150 else 132) * resources.displayMetrics.density / 3f
        box.addView(orb, LinearLayout.LayoutParams(size.toInt(), size.toInt()))
        box.addView(text(label, 13f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        })
        val anim = AlphaAnimation(0.72f, 1f).apply {
            duration = if (active) 1300 else 1800
            repeatMode = AlphaAnimation.REVERSE
            repeatCount = AlphaAnimation.INFINITE
        }
        orb.startAnimation(anim)
        return box
    }

    private fun primaryButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        setTextColor(Color.WHITE)
        isAllCaps = false
        background = rounded(primary, 22f, primary)
        setOnClickListener { action() }
        minHeight = 54
    }

    private fun smallCard(t: String, d: String, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(10, 15, 10, 15)
        background = rounded(surface, 18f, Color.rgb(230, 229, 238))
        setOnClickListener { action() }
        addView(text(t, 16f, ink).apply { gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD) })
        addView(text(d, 12f, muted).apply { gravity = Gravity.CENTER; setPadding(0, 4, 0, 0) })
    }

    private fun cardButton(t: String, d: String, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 14, 18, 14)
        background = rounded(surface, 18f, Color.rgb(230, 229, 238))
        setOnClickListener { action() }
        addView(text(t, 17f, ink).apply { setTypeface(null, Typeface.BOLD) })
        addView(text(d, 13f, muted).apply { setPadding(0, 5, 0, 0) })
    }

    private fun iconButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 20f
        setTextColor(primary)
        isAllCaps = false
        background = rounded(surface, 18f, Color.rgb(230, 229, 238))
        setOnClickListener { action() }
        minHeight = 48
    }

    private fun navButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(primary)
        textSize = 13f
        isAllCaps = false
        setOnClickListener { action() }
        setBackgroundColor(Color.TRANSPARENT)
    }

    private fun title(v: String, s: Float) = text(v, s, ink).apply { setTypeface(null, Typeface.BOLD); setPadding(0, 8, 0, 4) }
    private fun subtitle(v: String, s: Float) = text(v, s, muted).apply { setPadding(0, 0, 0, 18) }
    private fun section(v: String) = text(v, 19f, ink).apply { setTypeface(null, Typeface.BOLD); setPadding(0, 18, 0, 10) }
    private fun text(v: String, s: Float, c: Int) = TextView(this).apply { text = v; textSize = s; setTextColor(c) }

    private fun rounded(c: Int, r: Float, stroke: Int): GradientDrawable =
        GradientDrawable().apply { setColor(c); cornerRadius = r; setStroke(1, stroke) }

    private fun layoutParams(t: Int, l: Int, b: Int, r: Int) =
        LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(l, t, r, b) }

    private fun weightParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
}
