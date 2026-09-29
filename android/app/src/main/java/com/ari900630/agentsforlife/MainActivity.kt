package com.ari900630.agentsforlife

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.provider.Settings
import android.app.role.RoleManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.graphics.drawable.GradientDrawable
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private var speech: SpeechRecognizer? = null
    private val prefs by lazy { getSharedPreferences("agents_settings", MODE_PRIVATE) }
    private val bg = Color.rgb(245, 247, 251)
    private val card = Color.WHITE
    private val primary = Color.rgb(45, 91, 210)
    private val ink = Color.rgb(28, 35, 50)
    private val muted = Color.rgb(100, 110, 130)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildShell()
        showHome()
    }

    override fun onDestroy() {
        speech?.destroy()
        tts.shutdown()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22,18,22,18) }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1,0,1f))
        val nav = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setPadding(10,8,10,10); background=rounded(Color.WHITE,22f) }
        nav.addView(navButton("צ'אט\nעם הסוכנים") { showChat() }, weightParams())
        nav.addView(navButton("שיחה\nעם הסוכנים") { showVoice() }, weightParams())
        nav.addView(navButton("הגדרות") { showSettings() }, weightParams())
        root.addView(nav)
        setContentView(root)
    }

    private fun showHome() {
        content.removeAllViews()
        content.addView(title("Agents for Life",30f))
        content.addView(subtitle("מרכז שליטה אישי עם סוכני AI",16f))
        status=text("",16f,ink).apply{gravity=Gravity.CENTER;setPadding(18,18,18,18);background=rounded(card,16f)}
        content.addView(status,layoutParams(0,0,12,0))
        content.addView(section("הסוכנים שלך"))
        content.addView(cardButton("סוכני AI","10 סוגים מוכנים + יצירה חופשית"){showAgentsDialog()})
        content.addView(section("שליטה במכשיר"))
        content.addView(cardButton("Wi‑Fi","פתיחת הגדרות Wi‑Fi של Android"){AgentAction.openSettings(this,"wifi")})
        content.addView(cardButton("Bluetooth","פתיחת הגדרות Bluetooth של Android"){AgentAction.openSettings(this,"bluetooth")})
        content.addView(cardButton("צליל","פתיחת הגדרות הצליל של Android"){AgentAction.openSettings(this,"sound")})
        content.addView(cardButton("תצוגה","פתיחת הגדרות התצוגה של Android"){AgentAction.openSettings(this,"display")})
        content.addView(cardButton("שירות נגישות","מאפשר לסוכן לנהל אפליקציות לאחר אישור המשתמש"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
        content.addView(cardButton("הרשאת אנשי קשר","גישה לאנשי הקשר לפי הרשאת Android"){requestContacts()})
        content.addView(cardButton("הגדרות והרשאות","פתיחת הגדרות Android של האפליקציה"){startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:" + packageName)))})
        content.addView(section("פעולות מהירות"))
        content.addView(cardButton("Wi-Fi","פתיחת הגדרות Wi-Fi"){AgentAction.openSettings(this,"wifi")})
        content.addView(cardButton("Bluetooth","פתיחת הגדרות Bluetooth"){AgentAction.openSettings(this,"bluetooth")})
        content.addView(cardButton("צליל ותצוגה","ניהול עוצמת קול ותצוגה"){AgentAction.openSettings(this,"sound")})
        content.addView(cardButton("אנשי קשר","פתיחת אנשי הקשר"){startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("content://contacts/people")))})
        content.addView(cardButton("חיוג","פתיחת לוח החיוג"){startActivity(Intent(Intent.ACTION_DIAL))})
        content.addView(section("אפליקציות"))
        content.addView(cardButton("ניהול אפליקציות","חסימה/פתיחה של אפליקציות באמצעות שירות הנגישות"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
        getLaunchableApps().forEach { info ->
            val pkg=info.activityInfo.packageName
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(16,12,16,12);background=rounded(card,16f)}
            row.addView(text(info.loadLabel(packageManager).toString(),15f,Color.WHITE),LinearLayout.LayoutParams(0,-2,1f))
            row.addView(Switch(this).apply{text="חסום";setTextColor(ink);isChecked=isAppBlocked(pkg);setOnCheckedChangeListener{_,b->setAppBlocked(pkg,b)}})
            row.setOnClickListener { startActivity(packageManager.getLaunchIntentForPackage(pkg) ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+pkg))) }
            content.addView(row,layoutParams(0,0,8,0))
        }
        content.addView(section("מספרים חסומים"))
        val input=EditText(this).apply{hint="מספר לחסימה";setHintTextColor(Color.rgb(150,155,165));setTextColor(ink);setSingleLine();background=rounded(card,14f);setPadding(18,12,18,12)}
        content.addView(input,layoutParams(0,0,8,0))
        content.addView(cardButton("חסום מספר","הוסף לרשימת החסימה"){
            val n=input.text.toString().trim()
            if(n.isNotEmpty()){addBlockedNumber(n);input.text.clear();Toast.makeText(this,"המספר נוסף",Toast.LENGTH_SHORT).show();showHome()}
        })
        prefs.getStringSet("blocked_numbers",emptySet()).orEmpty().sorted().forEach { n ->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            row.addView(text(n,15f,ink),LinearLayout.LayoutParams(0,-2,1f))
            row.addView(Button(this).apply{text="הסר";setOnClickListener{removeBlockedNumber(n);showHome()}})
            content.addView(row)
        }
        refreshStatus()
    }

    private fun showChat() {
        content.removeAllViews()
        content.addView(title("צ'אט עם הסוכנים",27f))
        content.addView(subtitle("בחר סוכן, כתוב משימה וקבל תשובה.",15f))
        val agents=AgentStore.load(this).ifEmpty{AgentStore.seedTemplates(this);AgentStore.load(this)}
        val spinner=Spinner(this)
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,agents.map{it.name})
        content.addView(spinner,layoutParams(0,0,10,0))
        val endpoint=EditText(this).apply{hint="כתובת שרת AI";setHintTextColor(Color.GRAY);setTextColor(Color.WHITE);setSingleLine();setText(prefs.getString("agent_server",""));background=rounded(card,14f);setPadding(16,12,16,12)}
        content.addView(endpoint,layoutParams(0,0,10,0))
        val task=EditText(this).apply{hint="כתוב כאן לסוכן...";setHintTextColor(Color.GRAY);setTextColor(Color.WHITE);minLines=5;gravity=48;background=rounded(card,14f);setPadding(16,12,16,12)}
        content.addView(task,layoutParams(0,0,10,0))
        val result=text("התשובה תופיע כאן.",15f,Color.WHITE).apply{setPadding(16,16,16,16);background=rounded(card,16f)}
        content.addView(result,layoutParams(0,0,12,0))
        content.addView(cardButton("שלח לסוכן","הפעל את הסוכן שבחרת"){
            val a=agents[spinner.selectedItemPosition]
            val base=endpoint.text.toString().trim()
            val provider=prefs.getString("ai_provider","Gemini") ?: "Gemini"
            val key=prefs.getString("ai_key","") ?: ""
            val request=task.text.toString().trim()
            if(base.isEmpty()||request.isEmpty()){result.text="הזן כתובת שרת ומשימה.";return@cardButton}
            prefs.edit().putString("agent_server",base).apply()
            result.text="מפעיל את " + a.name + "..."
            Thread{
                val r=AgentApiClient.run(base,a.name,a.instructions,request,provider,key)
                runOnUiThread{result.text=r.fold({it},{e->"שגיאה: " + e.message})}
            }.start()
        })
    }

    private fun showVoice() {
        content.removeAllViews()
        content.addView(title("שיחה עם הסוכנים",27f))
        content.addView(subtitle("דבר אל הסוכן וקבל תשובה קולית.",15f))
        val agents=AgentStore.load(this).ifEmpty{AgentStore.seedTemplates(this);AgentStore.load(this)}
        val spinner=Spinner(this)
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,agents.map{it.name})
        content.addView(spinner,layoutParams(0,0,10,0))
        val endpoint=EditText(this).apply{hint="כתובת שרת AI";setHintTextColor(Color.GRAY);setTextColor(Color.WHITE);setSingleLine();setText(prefs.getString("agent_server",""));background=rounded(card,14f);setPadding(16,12,16,12)}
        content.addView(endpoint,layoutParams(0,0,10,0))
        val transcript=text("לחץ על המיקרופון והתחל לדבר.",16f,Color.WHITE).apply{setPadding(18,18,18,18);background=rounded(card,16f)}
        content.addView(transcript,layoutParams(0,0,10,0))
        content.addView(cardButton("התחל שיחה","דבר אל הסוכן"){
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),700);return@cardButton}
            startListening(spinner,endpoint,transcript)
        })
    }

    private fun startListening(spinner:Spinner,endpoint:EditText,transcript:TextView){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){transcript.text="זיהוי דיבור אינו זמין במכשיר.";return}
        speech?.destroy()
        speech=SpeechRecognizer.createSpeechRecognizer(this)
        speech?.setRecognitionListener(object:RecognitionListener{
            override fun onReadyForSpeech(p:Bundle?){transcript.text="מקשיב..."}
            override fun onBeginningOfSpeech(){transcript.text="אני מקשיב..."}
            override fun onRmsChanged(v:Float){}
            override fun onBufferReceived(b:ByteArray?){}
            override fun onEndOfSpeech(){}
            override fun onError(e:Int){transcript.text="לא הצלחתי לזהות דיבור. נסה שוב."}
            override fun onResults(b:Bundle?){
                val spoken=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if(spoken.isBlank()){transcript.text="לא זוהה טקסט.";return}
                transcript.text="אתה: " + spoken + "\n\nמעבד..."
                val agents=AgentStore.load(this@MainActivity)
                val a=agents.getOrNull(spinner.selectedItemPosition)
                val base=endpoint.text.toString().trim()
                val provider=prefs.getString("ai_provider","Gemini") ?: "Gemini"
                val key=prefs.getString("ai_key","") ?: ""
                if(a==null||(key.isEmpty()&&base.isEmpty())){transcript.text="הגדר ספק AI ומפתח API בהגדרות.";return}
                Thread{
                    val r=AgentApiClient.run(base,a.name,a.instructions,spoken)
                    runOnUiThread{r.fold(
                        {answer->transcript.text="אתה: " + spoken + "\n\n" + a.name + ": " + answer;speak(answer)},
                        {err->transcript.text="שגיאה: " + err.message}
                    )}
                }.start()
            }
            override fun onPartialResults(b:Bundle?){}
            override fun onEvent(t:Int,p:Bundle?){}
        })
        val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
        }
        speech?.startListening(intent)
    }

    private fun speak(value:String){if(::tts.isInitialized)tts.speak(value,TextToSpeech.QUEUE_FLUSH,null,"agent-answer")}

    private fun showSettings(){
        content.removeAllViews()
        content.addView(title("הגדרות",27f))
        content.addView(subtitle("חיבור AI, הרשאות ושירותי Agents for Life.",15f))
        content.addView(section("מנוע הבינה המלאכותית"))
        val provider=Spinner(this)
        provider.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Gemini — חינמי","OpenRouter — מודלים חינמיים","שרת עצמי"))
        val savedProvider=prefs.getString("ai_provider","Gemini") ?: "Gemini"
        provider.setSelection(if(savedProvider=="OpenRouter")1 else if(savedProvider=="Server")2 else 0)
        content.addView(provider,layoutParams(0,0,8,0))
        val apiKey=EditText(this).apply{hint="מפתח API";setHintTextColor(Color.rgb(150,155,165));setTextColor(ink);setSingleLine();inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;setText(prefs.getString("ai_key",""));background=rounded(card,14f);setPadding(16,12,16,12)}
        content.addView(apiKey,layoutParams(0,0,8,0))
        val endpoint=EditText(this).apply{hint="כתובת שרת AI — רק אם בחרת שרת עצמי";setHintTextColor(Color.rgb(150,155,165));setTextColor(ink);setSingleLine();setText(prefs.getString("agent_server",""));background=rounded(card,14f);setPadding(16,12,16,12)}
        content.addView(endpoint,layoutParams(0,0,10,0))
        content.addView(cardButton("שמור חיבור AI","הסוכנים ישתמשו בהגדרה הזו"){val p=when(provider.selectedItemPosition){1->"OpenRouter";2->"Server";else->"Gemini"};prefs.edit().putString("ai_provider",p).putString("ai_key",apiKey.text.toString().trim()).putString("agent_server",endpoint.text.toString().trim()).apply();Toast.makeText(this,"חיבור ה-AI נשמר",Toast.LENGTH_SHORT).show()})
        content.addView(cardButton("שירות נגישות","פתיחת הגדרות Android"){startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
        content.addView(cardButton("הרשאות האפליקציה","פתיחת הרשאות Android"){startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:" + packageName)))})
        content.addView(cardButton("ניהול חסימת שיחות","הגדרת Agents for Life כמסנן שיחות"){requestCallScreeningRole()})
        content.addView(cardButton("הגדרות שיחות","פתיחת הגדרות ברירת מחדל"){try{startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))}catch(_:Exception){startActivity(Intent(Settings.ACTION_SETTINGS))}})
        content.addView(cardButton("רענון מצב","בדיקת הרשאות ושירותים"){refreshStatus();Toast.makeText(this,"המצב עודכן",Toast.LENGTH_SHORT).show()})
    }

    private fun showAgentsDialog(){
        AgentStore.seedTemplates(this)
        val agents=AgentStore.load(this)
        val names=agents.map{it.name + " — " + it.type}.toMutableList()
        names.add(0,"+ צור סוכן חדש")
        AlertDialog.Builder(this).setTitle("הסוכנים שלי").setItems(names.toTypedArray()){_,which->
            if(which==0)showCreateAgentDialog() else Toast.makeText(this,"הסוכן " + agents[which-1].name + " נבחר",Toast.LENGTH_SHORT).show()
        }.setNegativeButton("סגור",null).show()
    }

    private fun showCreateAgentDialog(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(30,8,30,0)}
        val name=EditText(this).apply{hint="שם הסוכן"}
        val type=EditText(this).apply{hint="סוג הסוכן"}
        val ins=EditText(this).apply{hint="מה הסוכן צריך לעשות?";minLines=4}
        box.addView(name);box.addView(type);box.addView(ins)
        AlertDialog.Builder(this).setTitle("יצירת סוכן חדש").setView(box).setNegativeButton("ביטול",null).setPositiveButton("צור"){_,_->
            AgentStore.create(this,name.text.toString(),type.text.toString(),ins.text.toString())
            Toast.makeText(this,"הסוכן נוצר",Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun requestCallScreeningRole(){
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 901)
                } else Toast.makeText(this,"סינון שיחות פעיל",Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this,"סינון שיחות אינו זמין במכשיר",Toast.LENGTH_SHORT).show()
        } else startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }

    private fun refreshStatus(){
        val active=isAccessibilityServiceEnabled()
        val ba=prefs.getStringSet("blocked_apps",emptySet()).orEmpty().size
        val bn=prefs.getStringSet("blocked_numbers",emptySet()).orEmpty().size
        if(::status.isInitialized)status.text=if(active)
            "● שירות הנגישות פעיל\nאפליקציות חסומות: " + ba + "  |  מספרים חסומים: " + bn
        else
            "○ שירות הנגישות אינו פעיל\nהפעל אותו כדי לאפשר שליטה באפליקציות"
    }

    private fun getLaunchableApps()=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),PackageManager.MATCH_ALL).filter{it.activityInfo.packageName!=packageName}.distinctBy{it.activityInfo.packageName}.sortedBy{it.loadLabel(packageManager).toString()}
    private fun isAppBlocked(p:String)=prefs.getStringSet("blocked_apps",emptySet()).orEmpty().contains(p)
    private fun setAppBlocked(p:String,b:Boolean){val s=prefs.getStringSet("blocked_apps",emptySet()).orEmpty().toMutableSet();if(b)s.add(p)else s.remove(p);prefs.edit().putStringSet("blocked_apps",s).apply();refreshStatus()}
    private fun addBlockedNumber(n:String){val s=prefs.getStringSet("blocked_numbers",emptySet()).orEmpty().toMutableSet();s.add(n);prefs.edit().putStringSet("blocked_numbers",s).apply()}
    private fun removeBlockedNumber(n:String){val s=prefs.getStringSet("blocked_numbers",emptySet()).orEmpty().toMutableSet();s.remove(n);prefs.edit().putStringSet("blocked_numbers",s).apply()}
    private fun requestContacts(){if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS),42)else Toast.makeText(this,"הרשאת אנשי קשר כבר פעילה",Toast.LENGTH_SHORT).show()}
    private fun isAccessibilityServiceEnabled():Boolean{val m=getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager;val e=ComponentName(this,AgentAccessibilityService::class.java);return m.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any{val s=it.resolveInfo.serviceInfo;ComponentName(s.packageName,s.name)==e}}
    override fun onInit(status:Int){if(status==TextToSpeech.SUCCESS)tts.language=Locale.getDefault()}

    private fun title(v:String,s:Float)=text(v,s,ink).apply{setTypeface(null,android.graphics.Typeface.BOLD);setPadding(0,8,0,4)}
    private fun subtitle(v:String,s:Float)=text(v,s,muted).apply{setPadding(0,0,0,18)}
    private fun section(v:String)=text(v,20f,ink).apply{setTypeface(null,android.graphics.Typeface.BOLD);setPadding(0,18,0,10)}
    private fun text(v:String,s:Float,c:Int)=TextView(this).apply{text=v;textSize=s;setTextColor(c)}
    private fun cardButton(t:String,d:String,action:()->Unit)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,14,18,14);background=rounded(card,16f);setOnClickListener{action()};addView(text(t,17f,Color.WHITE).apply{setTypeface(null,android.graphics.Typeface.BOLD)});addView(text(d,13f,muted).apply{setPadding(0,5,0,0)})}.also{it.isClickable=true}
    private fun navButton(label:String,action:()->Unit)=Button(this).apply{text=label;setTextColor(Color.WHITE);textSize=13f;isAllCaps=false;setOnClickListener{action()};setBackgroundColor(Color.TRANSPARENT)}
    private fun rounded(c:Int,r:Float):GradientDrawable=GradientDrawable().apply{setColor(c);cornerRadius=r;setStroke(1,Color.rgb(225,229,238))}
    private fun layoutParams(t:Int,l:Int,b:Int,r:Int)=LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT).apply{setMargins(l,t,r,b)}
    private fun weightParams()=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)
}
