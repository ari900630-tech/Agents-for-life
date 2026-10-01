package com.ari900630.agentsforlife

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
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
    private lateinit var prefs: SharedPreferences
    private val defaultServerUrl = "https://agents-for-life.onrender.com"

    private val lightMode get() = prefs.getBoolean("light_mode", false)
    private val bg get() = if (lightMode) Color.rgb(247, 248, 252) else Color.rgb(8, 10, 18)
    private val surface get() = if (lightMode) Color.WHITE else Color.rgb(20, 23, 32)
    private val primary = Color.rgb(30, 55, 182)
    private val secondary = Color.rgb(80, 144, 173)
    private val deepBlue = Color.rgb(12, 31, 92)
    private val panel get() = if (lightMode) Color.WHITE else Color.rgb(16, 20, 31)
    private val ink get() = if (lightMode) Color.rgb(35, 38, 52) else Color.rgb(245, 246, 250)
    private val muted get() = if (lightMode) Color.rgb(105, 111, 130) else Color.rgb(157, 163, 180)

    private fun toggleTheme() {
        prefs.edit().putBoolean("light_mode", !lightMode).apply()
        recreate()
    }
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("agents_settings", MODE_PRIVATE)

        // Keep startup minimal and fail-safe. A startup exception must not make the
        // application appear to open and immediately disappear on the phone.
        try {
            buildShell()
            if (!prefs.getBoolean("permission_setup_seen", false)) {
                showPermissionSetup()
            } else {
                showHome()
            }
        } catch (t: Throwable) {
            android.util.Log.e("AgentsForLife", "Startup crash", t)
            showStartupError(t)
        }
    }

    private fun ensureTts() {
        if (tts != null) return
        runCatching {
            tts = TextToSpeech(this) { result ->
                if (result == TextToSpeech.SUCCESS) {
                    runCatching { tts?.language = Locale("he", "IL") }
                }
            }
        }.onFailure {
            android.util.Log.e("AgentsForLife", "TTS init failed", it)
        }
    }

    private fun showStartupError(error: Throwable) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(8, 10, 18))
        }
        screen.addView(text("Agents for Life", 26f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        })
        screen.addView(text(
            "הייתה תקלה בהפעלת האפליקציה.\n\n$message",
            15f, Color.rgb(220, 222, 232)
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 18)
        })
        screen.addView(primaryButton("נסה שוב") {
            recreate()
        }, LinearLayout.LayoutParams(-1, 56))
        screen.addView(text(
            "אם המסך הזה מופיע, האפליקציה נשארת פתוחה כדי שנוכל לזהות את התקלה במקום להיסגר.",
            12f, Color.rgb(157, 163, 180)
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 0)
        })
        setContentView(screen)
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
            background = rounded(Color.rgb(20, 23, 32), 26f, Color.rgb(50, 55, 75))
        }
        nav.addView(navButton("בית") { showHome() }, weightParams())
        nav.addView(navButton("סוכנים") { showAgentsDialog() }, weightParams())
        nav.addView(navButton("צ׳אט") { showChat() }, weightParams())
        root.addView(nav, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun showPermissionSetup() {
        content.removeAllViews()
        content.addView(title("הגדרת Agents for Life", 28f))
        content.addView(subtitle("לפני השימוש הראשון, אשר את ההרשאות הדרושות לסוכן.", 15f))

        val accessibilityOk = AgentAccessibilityService.isEnabled()
        val micOk = android.os.Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        content.addView(permissionCard(
            "שליטה במכשיר",
            if (accessibilityOk) "✓ מאושר" else "נדרש: שירות נגישות Android",
            accessibilityOk
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, layoutParams(0, 0, 10, 0))

        content.addView(permissionCard(
            "מיקרופון",
            if (micOk) "✓ מאושר" else "נדרש לדיבור עם הסוכן",
            micOk
        ) {
            requestMicrophonePermission()
        }, layoutParams(0, 0, 10, 0))

        content.addView(permissionCard(
            "חלון הסוכן הצף",
            "נכלל דרך שירות הנגישות — אין צורך בהרשאת חלון נפרדת",
            accessibilityOk
        ) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            layoutParams(0, 0, 10, 0))

        content.addView(primaryButton("אישור וסיום ההגדרה") {
            prefs.edit().putBoolean("permission_setup_seen", true).apply()
            showHome()
        }, layoutParams(0, 12, 0, 10))

        content.addView(cardButton(
            "בדוק הרשאות שוב",
            "אם זה עתה אישרת נגישות או מיקרופון, לחץ כאן לעדכון המצב."
        ) { showPermissionSetup() })
    }

    private fun requestMicrophonePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 701)
        }
    }

    private fun permissionCard(title: String, description: String, enabled: Boolean, action: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            background = rounded(surface, 20f, if (enabled) Color.rgb(190, 220, 198) else Color.rgb(225, 224, 236))
            setOnClickListener { action() }
            addView(text(title, 17f, ink).apply { setTypeface(null, Typeface.BOLD) })
            addView(text(description, 13f, if (enabled) Color.rgb(45, 110, 60) else muted).apply {
                setPadding(0, 6, 0, 0)
            })
        }

    private fun showHome() {
        content.removeAllViews()
        content.setPadding(18, 16, 18, 14)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(text("Agents for Life", 21f, ink).apply { setTypeface(null, Typeface.BOLD) })
        titleBox.addView(text("AI assistant", 12f, muted))
        top.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(iconButton("⚙") { showServerInfo() }, LinearLayout.LayoutParams(50, 50))
        content.addView(top, layoutParams(0, 0, 8, 0))

        content.addView(text("איך אפשר לעזור?", 28f, ink).apply {
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 10, 0, 2)
        })
        content.addView(text("דבר איתי או כתוב מה אתה רוצה שאעשה.", 14f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        })

        content.addView(createOrb("מוכן להקשיב", true), layoutParams(0, 0, 4, 0))

        content.addView(text("GPT-OSS-20B  •  Groq  ▾", 13f, Color.rgb(234, 236, 251)).apply {
            gravity = Gravity.CENTER
            setPadding(18, 11, 18, 11)
            background = rounded(deepBlue, 22f, secondary)
            setOnClickListener { showModelPicker() }
        }, layoutParams(0, 2, 10, 2))

        val task = EditText(this).apply {
            hint = "כתוב הודעה…"
            setHintTextColor(Color.rgb(159, 169, 205))
            setTextColor(ink)
            textSize = 16f
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.RIGHT
            background = rounded(panel, 22f, Color.rgb(69, 91, 120))
            setPadding(18, 15, 18, 15)
            setSingleLine(false)
        }
        content.addView(task, layoutParams(0, 0, 8, 0))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(iconButton("＋") { showVisionInfo() }, LinearLayout.LayoutParams(52, 52))
        actions.addView(Space(this), LinearLayout.LayoutParams(8, 1))
        actions.addView(primaryButton("שלח  →") {
            val orb = content.findViewWithTag<View>("home_orb") ?: task
            runAgent(task, TextView(this@MainActivity), orb)
        }, LinearLayout.LayoutParams(0, 56, 1f))
        actions.addView(Space(this), LinearLayout.LayoutParams(8, 1))
        actions.addView(iconButton("●") {
            startVoiceInput(task, TextView(this@MainActivity), content.findViewWithTag<View>("home_orb") ?: task)
        }, LinearLayout.LayoutParams(58, 58))
        content.addView(actions, layoutParams(0, 0, 10, 0))

        val suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        suggestions.addView(suggestionChip("שאל אותי משהו") { task.requestFocus() }, weightParams())
        suggestions.addView(Space(this), LinearLayout.LayoutParams(8, 1))
        suggestions.addView(suggestionChip("הסתכל על המסך") { showVisionInfo() }, weightParams())
        content.addView(suggestions, layoutParams(0, 0, 10, 0))

        if (!AgentAccessibilityService.isEnabled()) {
            content.addView(cardButton(
                "הפעל שליטה במכשיר",
                "נדרשת הרשאת נגישות כדי שהסוכן יוכל לבצע פעולות בטלפון."
            ) { showPermissionSetup() }, layoutParams(0, 0, 8, 0))
        }

        content.addView(text("מצב", 13f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 2, 0, 3)
        })
        status = text("", 12f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(12, 6, 12, 8)
        }
        content.addView(status)
        refreshStatus()
    }

    private var currentChatId: String? = null
    private var chatMessagesBox: LinearLayout? = null

    private fun showChat(chatId: String? = currentChatId) {
        var session = chatId?.let { ChatStore.find(this, it) }
        if (session == null) session = ChatStore.create(this)
        currentChatId = session.id
        content.removeAllViews()

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(iconButton("☰") { showChatHistory() }, LinearLayout.LayoutParams(52, 52))
        top.addView(text(session.title, 20f, ink).apply { gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(iconButton("＋") { startNewChat() }, LinearLayout.LayoutParams(52, 52))
        content.addView(top, layoutParams(0, 0, 8, 0))

        content.addView(text("GPT-OSS-20B  •  Groq  ▾", 13f, Color.rgb(234, 236, 251)).apply {
            gravity = Gravity.CENTER
            setPadding(18, 10, 18, 10)
            background = rounded(Color.rgb(12, 31, 92), 22f, Color.rgb(80, 144, 173))
            setOnClickListener { showModelPicker() }
        }, layoutParams(0, 0, 10, 8))

        val task = EditText(this).apply {
            hint = "כתוב הודעה…"; setHintTextColor(Color.rgb(159, 169, 205)); setTextColor(ink); textSize = 16f
            minLines = 2; maxLines = 5; gravity = Gravity.TOP or Gravity.RIGHT
            background = rounded(surface, 22f, Color.rgb(69, 91, 120)); setPadding(18, 14, 18, 14); setSingleLine(false)
        }

        val suggestions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        suggestions.addView(suggestionChip("שאל אותי משהו") { task.requestFocus() }, weightParams())
        suggestions.addView(Space(this), LinearLayout.LayoutParams(8, 1))
        suggestions.addView(suggestionChip("הסתכל על המסך") { showVisionInfo() }, weightParams())
        content.addView(suggestions, layoutParams(0, 0, 10, 0))
        content.addView(task, layoutParams(0, 0, 10, 0))

        val orb = createOrb("מוכן לשיחה", true)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actions.addView(iconButton("＋") { showVisionInfo() }, LinearLayout.LayoutParams(52, 52))
        actions.addView(Space(this), LinearLayout.LayoutParams(8, 1))
        actions.addView(primaryButton("שלח") { sendChatMessage(session!!.id, task, orb) }, LinearLayout.LayoutParams(0, 56, 1f))
        actions.addView(Space(this), LinearLayout.LayoutParams(8, 1))
        actions.addView(iconButton("●") { startVoiceInput(task, TextView(this), orb) }, LinearLayout.LayoutParams(64, 64))
        content.addView(actions)
    }

    private fun renderChatMessages(session: ChatSession) {
        val box = chatMessagesBox ?: return
        box.removeAllViews()
        if (session.messages.isEmpty()) {
            box.addView(text("שיחה חדשה", 24f, ink).apply { gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD); setPadding(0, 24, 0, 4) })
            box.addView(text("כתוב הודעה כדי להתחיל. השיחה תישמר אוטומטית.", 14f, muted).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, 18) })
            return
        }
        session.messages.forEach { msg ->
            val bubble = TextView(this).apply {
                text = msg.text; textSize = 15f; setTextColor(if (msg.role == "user") Color.WHITE else ink)
                setPadding(16, 13, 16, 13)
                background = rounded(if (msg.role == "user") primary else surface, 20f, if (msg.role == "user") primary else Color.rgb(220, 222, 232))
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = if (msg.role == "user") Gravity.END else Gravity.START
                addView(bubble, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(30, 4, 30, 4) })
            }
            box.addView(row)
        }
    }

    private fun sendChatMessage(sessionId: String, task: EditText, orb: View) {
        val request = task.text.toString().trim()
        if (request.isEmpty()) return
        ChatStore.addMessage(this, sessionId, "user", request)
        task.setText("")
        val agents = AgentStore.load(this).ifEmpty { AgentStore.seedTemplates(this); AgentStore.load(this) }
        val a = agents.firstOrNull { it.type == "assistant" } ?: agents.firstOrNull()
        val server = prefs.getString("agent_server", defaultServerUrl).orEmpty()
        if (a == null || server.isBlank()) return
        orb.alpha = 0.72f
        Thread {
            val r = AgentApiClient.run(server, a.name, a.instructions, request, "Server", "", "openai/gpt-oss-20b", "Groq")
            runOnUiThread {
                orb.alpha = 1f
                r.fold(
                    { answer -> ChatStore.addMessage(this, sessionId, "assistant", answer); showChat(sessionId); speak(answer) },
                    { e -> ChatStore.addMessage(this, sessionId, "assistant", "שגיאה: " + (e.message ?: "לא ידועה")); showChat(sessionId) }
                )
            }
        }.start()
    }

    private fun startNewChat() {
        val s = ChatStore.create(this)
        currentChatId = s.id
        showChat(s.id)
    }

    private fun showChatHistory() {
        val sessions = ChatStore.load(this)
        content.removeAllViews()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(iconButton("‹") { showChat() }, LinearLayout.LayoutParams(52, 52))
        top.addView(text("השיחות שלי", 24f, ink).apply { gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(iconButton("＋") { startNewChat() }, LinearLayout.LayoutParams(52, 52))
        content.addView(top, layoutParams(0, 0, 12, 0))
        if (sessions.isEmpty()) {
            content.addView(text("אין עדיין שיחות שמורות.", 16f, muted).apply { gravity = Gravity.CENTER; setPadding(0, 40, 0, 0) })
            return
        }
        sessions.forEach { chat ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(16, 14, 10, 14)
                background = rounded(surface, 18f, Color.rgb(220, 222, 232)); setOnClickListener { showChat(chat.id) }
            }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(text(chat.title, 17f, ink).apply { setTypeface(null, Typeface.BOLD) })
            info.addView(text(if (chat.messages.isEmpty()) "שיחה חדשה" else "\${chat.messages.size} הודעות", 12f, muted).apply { setPadding(0, 4, 0, 0) })
            card.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(iconButton("⋮") { showChatActions(chat) }, LinearLayout.LayoutParams(48, 48))
            content.addView(card, layoutParams(0, 0, 8, 0))
        }
    }

    private fun showChatActions(chat: ChatSession) {
        AlertDialog.Builder(this).setTitle(chat.title)
            .setItems(arrayOf("פתח שיחה", "מחק שיחה")) { _, which ->
                if (which == 0) showChat(chat.id) else { ChatStore.delete(this, chat.id); showChatHistory() }
            }.setNegativeButton("ביטול", null).show()
    }

    private fun showModelPicker() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 8, 20, 8)
            setBackgroundColor(Color.rgb(8, 10, 18))
        }
        box.addView(text("בחר מודל", 22f, ink).apply {
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 12)
        })
        val models = listOf(
            "GPT-OSS-20B" to "שיחה מהירה וחכמה",
            "Llama 3.3 70B" to "מודל כללי מתקדם",
            "Mixtral" to "מהיר למשימות טקסט"
        )
        models.forEach { (name, desc) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                background = rounded(Color.rgb(20, 23, 32), 18f, Color.rgb(50, 55, 75))
                setOnClickListener {
                    Toast.makeText(this@MainActivity, "נבחר: $name", Toast.LENGTH_SHORT).show()
                }
                addView(text(name, 16f, ink).apply { setTypeface(null, Typeface.BOLD) })
                addView(text(desc, 12f, muted).apply { setPadding(0, 4, 0, 0) })
            }
            box.addView(card, layoutParams(0, 0, 8, 0))
        }
        AlertDialog.Builder(this).setView(box).setNegativeButton("סגור", null).show()
    }

    private fun showVisionInfo() {
        AlertDialog.Builder(this)
            .setTitle("Vision — ראיית מסך")
            .setMessage("הסוכן יכול לעבוד עם תצוגת המסך דרך שירות הנגישות לאחר שאישרת את ההרשאה.")
            .setPositiveButton("פתח הרשאות") { _, _ -> showPermissionSetup() }
            .setNegativeButton("סגור", null)
            .show()
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
        Thread {
            val r = AgentApiClient.run(server, a.name, a.instructions, request, "Server", "", "openai/gpt-oss-20b", "Groq")
            runOnUiThread {
                orb.alpha = 1f
                r.fold(
                    { answer -> AgentActionBridge.offerActions(this, answer) { result.text = it; speak(it) } },
                    { e -> result.text = "שגיאה: " + (e.message ?: "לא ידועה"); speak(result.text.toString()) }
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
        if (clean.isBlank()) return
        ensureTts()
        runCatching {
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "agent-answer")
        }.onFailure {
            android.util.Log.e("AgentsForLife", "TTS speak failed", it)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 701) {
            Toast.makeText(
                this,
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "המיקרופון אושר." else "המיקרופון לא אושר.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
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
            background = rounded(surface, 16f, Color.rgb(50, 55, 75))
            setPadding(16, 12, 16, 12)
        }
        content.addView(endpoint, layoutParams(0, 0, 10, 0))

        content.addView(primaryButton("שמור כתובת שרת") {
            prefs.edit().putString("agent_server", endpoint.text.toString().trim())
                .putString("ai_provider", "Server").remove("ai_key").apply()
            Toast.makeText(this, "נשמר — אין צורך במפתח API", Toast.LENGTH_SHORT).show()
            refreshStatus()
        }, layoutParams(0, 0, 10, 0))

        content.addView(cardButton("הרשאות ושליטה במכשיר", "פתיחת מסך ההרשאות של Agents for Life") {
            showPermissionSetup()
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
        val access = if (AgentAccessibilityService.isEnabled()) "✓ נגישות פעילה" else "○ נגישות לא פעילה"
        val mic = if (android.os.Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "✓ מיקרופון מאושר" else "○ מיקרופון לא מאושר"
        status.text = if (server.isBlank()) "$access\n$mic\n○ שרת AI לא מוגדר"
        else "$access\n$mic\n● שרת AI מוגדר"
    }

    private fun suggestionChip(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(234, 236, 251))
        setPadding(10, 11, 10, 11)
        background = rounded(Color.rgb(12, 31, 92), 20f, Color.rgb(69, 105, 150))
        setOnClickListener { action() }
    }

    private fun createOrb(label: String, active: Boolean): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 4)
        }
        val orb = TextView(this).apply {
            tag = "home_orb"
            text = "✦"
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(30, 55, 182),
                    Color.rgb(12, 31, 92),
                    Color.rgb(80, 144, 173),
                    Color.rgb(30, 55, 182)
                )
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(2, Color.rgb(80, 144, 173))
            }
            elevation = 10f
        }
        val size = (if (active) 178 else 150) * resources.displayMetrics.density
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
        background = rounded(surface, 18f, Color.rgb(50, 55, 75))
        setOnClickListener { action() }
        addView(text(t, 16f, ink).apply { gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD) })
        addView(text(d, 12f, muted).apply { gravity = Gravity.CENTER; setPadding(0, 4, 0, 0) })
    }

    private fun cardButton(t: String, d: String, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18, 14, 18, 14)
        background = rounded(panel, 18f, Color.rgb(50, 55, 75))
        setOnClickListener { action() }
        addView(text(t, 17f, ink).apply { setTypeface(null, Typeface.BOLD) })
        addView(text(d, 13f, muted).apply { setPadding(0, 5, 0, 0) })
    }

    private fun iconButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 20f
        setTextColor(Color.rgb(234, 236, 251))
        isAllCaps = false
        background = rounded(panel, 18f, Color.rgb(50, 55, 75))
        setOnClickListener { action() }
        minHeight = 48
    }

    private fun navButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.rgb(159, 169, 205))
        textSize = 12f
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
