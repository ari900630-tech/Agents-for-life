const KEY='agents-for-life:v1';
const defaults=[
 {id:'research',name:'חוקר AI',instructions:'אתה סוכן מחקר. חפש מידע אמין, השווה מקורות, והצג תשובה קצרה ומעשית.',webSearch:true},
 {id:'writer',name:'כותב AI',instructions:'אתה סוכן כתיבה. הפוך דרישות לא ברורות לטקסט ברור, מקצועי ומוכן לשימוש.',webSearch:false},
 {id:'planner',name:'מתכנן AI',instructions:'אתה סוכן תכנון. פרק מטרה לשלבים ברורים, סדר עדיפויות ופעולות הבאות.',webSearch:false}
];
let state=JSON.parse(localStorage.getItem(KEY)||'null')||{agents:defaults,selected:'research',history:[]};
const $=s=>document.querySelector(s);
function save(){localStorage.setItem(KEY,JSON.stringify(state));}
function selected(){return state.agents.find(a=>a.id===state.selected)||state.agents[0];}
function render(){
 const a=selected(); state.selected=a.id; save();
 $('#agentList').innerHTML=state.agents.map(x=>`<button class="agent ${x.id===a.id?'active':''}" data-id="${x.id}"><strong>${escapeHtml(x.name)}</strong><small>${escapeHtml(x.instructions)}</small></button>`).join('');
 document.querySelectorAll('.agent').forEach(b=>b.onclick=()=>{state.selected=b.dataset.id;const x=selected();$('#webSearch').checked=x.webSearch;render()});
 $('#agentHeader').innerHTML=`<h2>${escapeHtml(a.name)}</h2><span>${escapeHtml(a.instructions)}</span>`;
 $('#webSearch').checked=a.webSearch;
 renderHistory();
}
function renderHistory(){const h=$('#history');if(!state.history.length){h.innerHTML='<div class="empty-history">עדיין אין משימות.</div>';return}h.innerHTML=state.history.slice().reverse().map(x=>`<div class="history-item"><strong>${escapeHtml(x.agent)}</strong><small>${new Date(x.time).toLocaleString('he-IL')}</small><p>${escapeHtml(x.task)}</p></div>`).join('')}
function escapeHtml(v){return String(v).replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]))}
$('#newAgent').onclick=()=>{$('#agentForm').reset();$('#agentDialog').showModal()};
$('#agentForm').onsubmit=e=>{e.preventDefault();const a={id:crypto.randomUUID(),name:$('#agentName').value.trim(),instructions:$('#agentInstructions').value.trim(),webSearch:$('#agentWeb').checked};if(!a.name||!a.instructions)return;state.agents.push(a);state.selected=a.id;save();$('#agentDialog').close();render()};
$('#clearHistory').onclick=()=>{state.history=[];save();renderHistory()};
$('#run').onclick=async()=>{const task=$('#task').value.trim();if(!task)return;const a=selected();const result=$('#result');result.className='result';result.textContent='הסוכן עובד…';$('#run').disabled=true;try{const r=await fetch('/api/run',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({agent:{...a,webSearch:$('#webSearch').checked},task})});const data=await r.json();if(!r.ok)throw new Error(data.error||'שגיאה');result.textContent=data.output;state.history.push({agent:a.name,task,time:Date.now()});state.history=state.history.slice(-30);save();renderHistory()}catch(e){result.textContent='שגיאה: '+e.message}finally{$('#run').disabled=false}};
async function health(){try{const r=await fetch('/api/health');const d=await r.json();$('#status').textContent=d.configured?'מחובר ל-AI':'נדרש API key'}catch{$('#status').textContent='השרת לא זמין'}}
render();health();
