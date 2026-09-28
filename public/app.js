const KEY='agents-for-life:v2';
const templates=[
 ['research','חוקר ומנתח','מחקר, מקורות, השוואות ומסקנות','אתה חוקר מקצועי. חפש מקורות רלוונטיים, בדוק עובדות, השווה מידע והצג מסקנות ברורות.'],
 ['writer','כותב תוכן','פוסטים, מאמרים, הודעות ותסריטים','אתה כותב מקצועי. הפוך דרישות לטקסט ברור, מדויק, טבעי ומוכן לפרסום.'],
 ['coder','מתכנת','קוד, תיקון באגים ותכנון תוכנה','אתה מפתח תוכנה. נתח דרישות, הצע פתרון עובד, בדוק מקרי קצה והצג קוד שימושי.'],
 ['planner','מתכנן','תוכניות עבודה, פרויקטים ומשימות','אתה מנהל פרויקטים. פרק מטרה לשלבים, סדר עדיפויות, תלויות ותוצאה סופית.'],
 ['marketing','שיווק','רעיונות, קמפיינים, קהלים ותוכן','אתה מומחה שיווק. נתח קהל, הצע מסרים, רעיונות וקמפיינים שניתן לבצע.'],
 ['social','רשתות חברתיות','Instagram, TikTok, YouTube ותוכן קצר','אתה מנהל תוכן לרשתות חברתיות. צור רעיונות, תסריטים, כותרות ולוחות פרסום.'],
 ['study','מורה אישי','לימוד, הסברים, תרגול וסיכומים','אתה מורה אישי. הסבר בפשטות, התאם לרמה, שאל שאלות ובנה תרגול.'],
 ['translator','מתרגם','תרגום ושיפור ניסוח','אתה מתרגם ועורך. שמור משמעות, הקשר וטון והחזר טקסט טבעי בשפת היעד.'],
 ['data','מנתח נתונים','טבלאות, נתונים, מגמות ודוחות','אתה אנליסט. ארגן נתונים, מצא מגמות, בדוק חריגות והצג מסקנות מספריות.'],
 ['assistant','עוזר אישי','משימות יומיומיות וקבלת החלטות','אתה עוזר אישי. הפוך בקשות לפעולות ברורות, שאל רק כשחסר מידע מהותי והצע צעד הבא.'],
 ['product','מנהל מוצר','רעיונות, אפיון, UX ותעדוף','אתה מנהל מוצר. הפוך רעיון לאפיון, משתמשים, דרישות, זרימות ותוכנית ביצוע.'],
 ['automation','אוטומציות','תהליכים, API וחיבורים','אתה מומחה אוטומציה. תכנן תהליכים חוזרים, אינטגרציות ופעולות עם בדיקות ושחזור מתקלות.']
];
const defaults=templates.slice(0,3).map(([id,name,_desc,instructions])=>({id,name,instructions,webSearch:id==='research'}));
let state=JSON.parse(localStorage.getItem(KEY)||'null')||{agents:defaults,selected:defaults[0].id,history:[]};
const $=s=>document.querySelector(s); const esc=v=>String(v).replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
function save(){localStorage.setItem(KEY,JSON.stringify(state));}
function selected(){return state.agents.find(a=>a.id===state.selected)||state.agents[0];}
function renderTemplates(){ $('#templateCount').textContent=templates.length; $('#templateList').innerHTML=templates.map(t=>`<button class="template" data-t="${t[0]}"><b>${esc(t[1])}</b><small>${esc(t[2])}</small></button>`).join(''); document.querySelectorAll('.template').forEach(b=>b.onclick=()=>addTemplate(b.dataset.t)); }
function addTemplate(id){const t=templates.find(x=>x[0]===id); if(!t)return; const a={id:crypto.randomUUID(),name:t[1],instructions:t[3],webSearch:id==='research'}; state.agents.push(a);state.selected=a.id;save();render();}
function render(){const a=selected();if(!a)return;state.selected=a.id;save();$('#agentCount').textContent=state.agents.length;$('#taskCount').textContent=state.history.length;$('#agentList').innerHTML=state.agents.map(x=>`<button class="agent ${x.id===a.id?'active':''}" data-id="${x.id}"><strong>${esc(x.name)}</strong><small>${esc(x.instructions)}</small></button>`).join('');document.querySelectorAll('.agent').forEach(b=>b.onclick=()=>{state.selected=b.dataset.id;render()});$('#agentHeader').innerHTML=`<h2>${esc(a.name)}</h2><span>${esc(a.instructions)}</span>`;$('#webSearch').checked=!!a.webSearch;renderHistory();}
function renderHistory(){const h=$('#history');h.innerHTML=state.history.length?state.history.slice().reverse().map(x=>`<div class="history-item"><strong>${esc(x.agent)}</strong><small>${new Date(x.time).toLocaleString('he-IL')}</small><p>${esc(x.task)}</p></div>`).join(''):'<div class="empty-history">עדיין אין משימות.</div>';$('#taskCount').textContent=state.history.length;}
$('#newAgent').onclick=()=>{$('#agentForm').reset();$('#agentDialog').showModal()};
$('#agentForm').onsubmit=e=>{e.preventDefault();const a={id:crypto.randomUUID(),name:$('#agentName').value.trim(),instructions:$('#agentInstructions').value.trim(),webSearch:$('#agentWeb').checked};if(!a.name||!a.instructions)return;state.agents.push(a);state.selected=a.id;save();$('#agentDialog').close();render()};
$('#duplicate').onclick=()=>{const a=selected();const copy={...a,id:crypto.randomUUID(),name:a.name+' — עותק'};state.agents.push(copy);state.selected=copy.id;save();render()};
$('#deleteAgent').onclick=()=>{if(state.agents.length<=1)return alert('צריך להשאיר לפחות סוכן אחד.');const a=selected();state.agents=state.agents.filter(x=>x.id!==a.id);state.selected=state.agents[0].id;save();render()};
$('#clearHistory').onclick=()=>{state.history=[];save();renderHistory()};
$('#run').onclick=async()=>{const task=$('#task').value.trim();if(!task)return;const a=selected(),result=$('#result');result.className='result';result.textContent='הסוכן עובד…';$('#run').disabled=true;try{const r=await fetch('/api/run',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({agent:{...a,webSearch:$('#webSearch').checked},task})});const data=await r.json();if(!r.ok)throw new Error(data.error||'שגיאה');result.textContent=data.output;state.history.push({agent:a.name,task,time:Date.now()});state.history=state.history.slice(-100);save();renderHistory()}catch(e){result.textContent='שגיאה: '+e.message}finally{$('#run').disabled=false}};
async function health(){try{const r=await fetch('/api/health');const d=await r.json();$('#status').textContent=d.configured?'מחובר ל-AI':'נדרש חיבור AI'}catch{$('#status').textContent='השרת לא זמין'}}
renderTemplates();render();health();