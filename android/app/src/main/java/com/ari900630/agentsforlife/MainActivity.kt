package com.ari900630.agentsforlife

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("agents_settings", MODE_PRIVATE) }
    private val defaultServerUrl = "https://agents-for-life.onrender.com"
    private val bg = Color.rgb(10, 12, 22)
    private val card = Color.rgb(18, 21, 35)
    private val primary = Color.rgb(116, 92, 255)
    private val ink = Color.WHITE
    private val muted = Color.rgb(170, 176, 200)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildShell()
        showHome()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18,18,18,18) }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1,0,1f))
        val nav = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setPadding(10,10,10,12); background=rounded(card,26f) }
        nav.addView(navButton("בית") { showHome() }, weightParams())
        nav.addView(navButton("סוכנים") { showAgentsDialog() }, weightParams())
        nav.addView(navButton("צ׳אט") { showChat() }, weightParams())
        root.addView(nav)
        setContentView(root)
    }

    private fun showHome() {
        content.removeAllViews()
        content.addView(title("Agents for Life",30f))
        content.addView(subtitle("מרכז סוכנים חכם",15f))
        val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(22,22,22,22);background=gradientCard()}
        hero.addView(text("מה המשימה שלך?",25f,ink).apply{setTypeface(null,android.graphics.Typeface.BOLD)})
        hero.addView(text("אין צורך לבחור סוכן או מודל. כתוב מה אתה רוצה והמערכת מפעילה את הסוכן המתאים.",14f,muted).apply{setPadding(0,8,0,16)})
        hero.addView(cardButton("✦ התחל משימה","המערכת מטפלת בשאר"){showChat()})
        content.addView(hero,layoutParams(0,0,14,0))
        content.addView(section("הסוכנים שלך"))
        content.addView(cardButton("מרכז הסוכנים","סוכני מחקר, כתיבה, תכנות, תכנון, שיווק ועוד"){showAgentsDialog()})
        content.addView(section("מצב המערכת"))
        status=text("",15f,ink).apply{gravity=Gravity.CENTER;setPadding(18,18,18,18);background=rounded(card,18f)}
        content.addView(status,layoutParams(0,0,12,0))
        content.addView(cardButton("הגדרות שרת","כתובת השרת בלבד — אין מפתחות או בחירת מודל בטלפון"){showServerInfo()})
        refreshStatus()
    }

    private fun showChat() {
        content.removeAllViews()
        content.addView(title("משימה חדשה",28f))
        content.addView(subtitle("הסוכן המתאים נבחר אוטומטית.",15f))
        content.addView(text("AUTO • GROQ • GPT-OSS-20B",13f,muted).apply{setPadding(14,10,14,10);background=rounded(card,14f)},layoutParams(0,0,10,0))
        val task=EditText(this).apply{hint="מה אתה רוצה שהסוכן יעשה?";setHintTextColor(Color.rgb(150,155,165));setTextColor(ink);minLines=7;gravity=48;background=rounded(card,18f);setPadding(18,16,18,16)}
        content.addView(task,layoutParams(0,0,10,0))
        val result=text("התשובה תופיע כאן.",15f,ink).apply{setPadding(18,18,18,18);background=rounded(card,18f)}
        content.addView(result,layoutParams(0,0,12,0))
        content.addView(cardButton("הפעל סוכן","ללא בחירת סוכן או מודל"){
            val agents=AgentStore.load(this).ifEmpty{AgentStore.seedTemplates(this);AgentStore.load(this)}
            val a=agents.firstOrNull{it.type=="assistant"} ?: agents.firstOrNull()
            val request=task.text.toString().trim()
            val server=prefs.getString("agent_server",defaultServerUrl).orEmpty()
            if(request.isEmpty()){result.text="כתוב משימה.";return@cardButton}
            if(a==null){result.text="לא נמצא סוכן.";return@cardButton}
            if(server.isBlank()){result.text="שרת ה-AI עדיין לא מוגדר באפליקציה.";return@cardButton}
            result.text="הסוכן עובד…"
            Thread{
                val r=AgentApiClient.run(server,a.name,a.instructions,request,"Server","","openai/gpt-oss-20b","Groq")
                runOnUiThread{r.fold({answer->AgentActionBridge.offerActions(this,answer){result.text=it}},{e->result.text="שגיאה: "+(e.message ?: "לא ידועה")})}
            }.start()
        })
    }

    private fun showServerInfo(){
        content.removeAllViews()
        content.addView(title("חיבור מערכת",27f))
        content.addView(subtitle("המשתמש לא צריך להגדיר מפתח API או לבחור מודל.",15f))
        val endpoint=EditText(this).apply{hint="כתובת שרת AI";setHintTextColor(Color.rgb(150,155,165));setTextColor(ink);setSingleLine();setText(prefs.getString("agent_server",defaultServerUrl));background=rounded(card,14f);setPadding(16,12,16,12)}
        content.addView(endpoint,layoutParams(0,0,10,0))
        content.addView(cardButton("הפעל שליטה במכשיר","נדרש אישור חד-פעמי בהגדרות נגישות Android"){ startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        content.addView(cardButton("שמור כתובת שרת","זה הדבר היחיד שנדרש מההתקנה"){
            prefs.edit().putString("agent_server",endpoint.text.toString().trim()).putString("ai_provider","Server").remove("ai_key").apply()
            Toast.makeText(this,"נשמר — אין צורך במפתח API",Toast.LENGTH_SHORT).show()
            refreshStatus()
        })
    }

    private fun showAgentsDialog(){
        AgentStore.seedTemplates(this)
        val agents=AgentStore.load(this)
        val names=agents.map{it.name + " — " + it.type}.toMutableList()
        names.add(0,"+ צור סוכן חדש")
        AlertDialog.Builder(this).setTitle("הסוכנים שלי").setItems(names.toTypedArray()){_,which->
            if(which==0)showCreateAgentDialog() else Toast.makeText(this,"הסוכן "+agents[which-1].name+" נבחר",Toast.LENGTH_SHORT).show()
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

    private fun refreshStatus(){
        val server=prefs.getString("agent_server","").orEmpty()
        status.text=if(server.isBlank()) "○ שרת AI לא מוגדר\nהיכנס לחיבור מערכת והוסף כתובת שרת"
        else "● שרת AI מוגדר\nאין צורך בהרשאות מיוחדות או במפתח API בטלפון"
    }

    private fun title(v:String,s:Float)=text(v,s,ink).apply{setTypeface(null,android.graphics.Typeface.BOLD);setPadding(0,8,0,4)}
    private fun subtitle(v:String,s:Float)=text(v,s,muted).apply{setPadding(0,0,0,18)}
    private fun section(v:String)=text(v,20f,ink).apply{setTypeface(null,android.graphics.Typeface.BOLD);setPadding(0,18,0,10)}
    private fun text(v:String,s:Float,c:Int)=TextView(this).apply{text=v;textSize=s;setTextColor(c)}
    private fun cardButton(t:String,d:String,action:()->Unit)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,14,18,14);background=rounded(card,16f);setOnClickListener{action()};addView(text(t,17f,ink).apply{setTypeface(null,android.graphics.Typeface.BOLD)});addView(text(d,13f,muted).apply{setPadding(0,5,0,0)})}.also{it.isClickable=true}
    private fun navButton(label:String,action:()->Unit)=Button(this).apply{text=label;setTextColor(primary);textSize=13f;isAllCaps=false;setOnClickListener{action()};setBackgroundColor(Color.TRANSPARENT)}
    private fun gradientCard():GradientDrawable=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(40,35,88),Color.rgb(24,73,110))).apply{cornerRadius=24f;setStroke(1,Color.rgb(75,82,125))}
    private fun rounded(c:Int,r:Float):GradientDrawable=GradientDrawable().apply{setColor(c);cornerRadius=r;setStroke(1,Color.rgb(45,50,72))}
    private fun layoutParams(t:Int,l:Int,b:Int,r:Int)=LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT).apply{setMargins(l,t,r,b)}
    private fun weightParams()=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)
}
