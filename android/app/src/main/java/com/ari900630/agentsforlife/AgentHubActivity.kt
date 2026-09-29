package com.ari900630.agentsforlife

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*

class AgentHubActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("agents_settings", MODE_PRIVATE) }
    private val agents = listOf(
        "חוקר ומנתח" to "חקור את המשימה, בדוק עובדות והצג מסקנות ברורות.",
        "כותב תוכן" to "כתוב תוכן ברור, מדויק ומוכן לשימוש.",
        "מתכנת" to "פתור בעיות תכנות, הסבר את הפתרון והצע קוד תקין.",
        "מתכנן" to "פרק את המשימה לשלבים מעשיים ובצע תכנון מסודר.",
        "שיווק" to "בנה רעיונות, מסרים ותוכנית שיווקית מעשית.",
        "רשתות חברתיות" to "צור תוכן לרשתות חברתיות עם ניסוח מתאים לפלטפורמה.",
        "מורה אישי" to "הסבר את הנושא בהדרגה, עם דוגמאות ותרגול.",
        "מתרגם" to "תרגם תוך שמירה על משמעות, הקשר וסגנון.",
        "מנתח נתונים" to "נתח נתונים, מצא דפוסים והצג מסקנות.",
        "עוזר אישי" to "עזור למשתמש לבצע את המשימה בצורה מעשית ומסודרת.",
        "מנהל מוצר" to "הגדר דרישות, משימות וסדרי עדיפויות למוצר.",
        "אוטומציות" to "תכנן תהליך אוטומטי עם שלבים ברורים ותנאים."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24); setBackgroundColor(Color.rgb(9,15,30)) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this); scroll.addView(content)
        content.addView(tv("סוכני AI",30f,Color.WHITE))
        content.addView(tv("בחר סוכן, כתוב משימה והפעל אותו דרך שרת ה-AI.",15f,Color.LTGRAY), params(0,0,12,0))
        val endpoint = EditText(this).apply { hint="כתובת שרת, לדוגמה https://example.com"; setText(prefs.getString("agent_server","")); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setSingleLine(true) }
        content.addView(endpoint,params(0,0,12,0))
        val task = EditText(this).apply { hint="מה אתה רוצה שהסוכן יעשה?"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); minLines=4; gravity=48 }
        content.addView(task,params(0,0,12,0))
        val result = tv("",15f,Color.WHITE); content.addView(result,params(0,0,12,0))
        agents.forEach { (name,instructions) ->
            content.addView(Button(this).apply {
                text=name
                setOnClickListener {
                    val base=endpoint.text.toString().trim(); val request=task.text.toString().trim()
                    if(base.isBlank()||request.isBlank()){ result.text="הזן כתובת שרת ומשימה."; return@setOnClickListener }
                    prefs.edit().putString("agent_server",base).apply(); result.text="מפעיל את $name..."
                    Thread { val response=AgentApiClient.run(base,name,instructions,request); runOnUiThread { result.text=response.fold({"✓ $name\n\n$it"},{"✕ ${it.message ?: "שגיאה"}"}) } }.start()
                }
            },params(0,0,8,0))
        }
        content.addView(Button(this).apply { text="צור סוכן מותאם אישית"; setOnClickListener { createCustomAgent() } },params(16,0,8,0))
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
    }

    private fun createCustomAgent() {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,8,24,0)}
        val name=EditText(this).apply{hint="שם הסוכן"}; val instructions=EditText(this).apply{hint="מה הסוכן צריך לעשות?";minLines=3}
        box.addView(name); box.addView(instructions)
        android.app.AlertDialog.Builder(this).setTitle("סוכן חדש").setView(box).setNegativeButton("ביטול",null).setPositiveButton("צור"){_,_->
            val n=name.text.toString().trim(); val i=instructions.text.toString().trim()
            if(n.isNotEmpty()&&i.isNotEmpty()) Toast.makeText(this,"הסוכן $n נוצר.",Toast.LENGTH_SHORT).show()
        }.show()
    }
    private fun tv(value:String,size:Float,color:Int)=TextView(this).apply{text=value;textSize=size;setTextColor(color)}
    private fun params(t:Int,l:Int,b:Int,r:Int)=LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT).apply{setMargins(l,t,r,b)}
}
