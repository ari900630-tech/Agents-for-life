import express from 'express';
import OpenAI from 'openai';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
app.use(express.json({ limit: '2mb' }));
app.use(express.static(path.join(__dirname, 'public')));

const port = process.env.PORT || 3000;
const openRouterModel = process.env.OPENROUTER_MODEL || 'openrouter/free';
const groqModel = process.env.GROQ_MODEL || 'openai/gpt-oss-20b';
const geminiModel = process.env.GEMINI_MODEL || 'gemini-2.5-flash-lite';
const ollamaUrl = (process.env.OLLAMA_URL || 'http://127.0.0.1:11434').replace(/\/$/, '');
const ollamaModel = process.env.OLLAMA_MODEL || 'gemma4';

function providers() {
  return [
    process.env.OPENROUTER_API_KEY && 'OpenRouter Free',
    process.env.GEMINI_API_KEY && 'Google Gemini Free',
    process.env.OLLAMA_URL && 'Ollama Local',
    process.env.OPENAI_API_KEY && 'OpenAI',
    process.env.GROQ_API_KEY && 'Groq'
  ].filter(Boolean);
}

app.get('/api/health', (_req, res) => {
  res.json({ ok: true, configured: providers().length > 0, providers: providers() });
});

function promptFor(agent, task) {
  return `You are the agent named "${agent.name}".\nYour role and instructions:\n${agent.instructions}\n\nExecute the user's task directly. Be concrete, concise, and action-oriented. If a requested action requires an external account, credential, approval, or human confirmation that you do not have, clearly identify the missing step instead of pretending it was completed. Never claim an external action happened unless an available tool actually completed it.\n\nFor Android device control, when the user explicitly requests an action, emit the exact marker [[DEVICE_ACTION:{"type":"ACTION_TYPE",...}]]. Allowed types: OPEN_SETTINGS with setting wifi, bluetooth, sound, display, accessibility; CALL with number; OPEN_URL with http/https URL; LAUNCH_APP with Android package; HOME; BACK; RECENTS; NOTIFICATIONS; QUICK_SETTINGS; POWER_DIALOG; LOCK_SCREEN; SCREENSHOT; SHARE_TEXT with text; SMS with number and optional body; EMAIL with optional to, subject, body; MAP with query; TYPE_TEXT with text to type into the currently focused field; SEND_TEXT with text to type and then send; CLICK_TEXT with visible label/content description to click (use | for alternatives); LONG_CLICK_TEXT with visible text to long-press; OPEN_CHAT_MENU to press the chat three-dots/more-options button; PIN to press a pin/הצמד action; PRESS_SEND to press the send button. TYPE_TEXT, SEND_TEXT, CLICK_TEXT, LONG_CLICK_TEXT, OPEN_CHAT_MENU, PIN and PRESS_SEND execute directly without repeated confirmation. Multiple markers can be emitted in one response and must execute in order. Do not emit markers for unrequested actions.\n\nUser task:\n${task.trim()}`;
}

async function runOpenRouter(prompt, webSearch, model = openRouterModel) {
  const client = new OpenAI({ apiKey: process.env.OPENROUTER_API_KEY, baseURL: 'https://openrouter.ai/api/v1' });
  const response = await client.chat.completions.create({
    model,
    messages: [{ role: 'user', content: prompt }],
    ...(webSearch ? { extra_headers: { 'HTTP-Referer': 'https://github.com/ari900630-tech/Agents-for-life', 'X-Title': 'Agents for Life' } } : {})
  });
  return response.choices?.[0]?.message?.content || '';
}

async function runGemini(prompt, model = geminiModel) {
  const selectedModel = model.startsWith('models/') ? model.slice(7) : model;
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(selectedModel)}:generateContent?key=${encodeURIComponent(process.env.GEMINI_API_KEY)}`;
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ contents: [{ parts: [{ text: prompt }] }] })
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data?.error?.message || `Gemini HTTP ${response.status}`);
  return data?.candidates?.[0]?.content?.parts?.map(p => p.text || '').join('') || '';
}

async function runOllama(prompt) {
  const response = await fetch(`${ollamaUrl}/api/chat`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ model: ollamaModel, messages: [{ role: 'user', content: prompt }], stream: false })
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data?.error || `Ollama HTTP ${response.status}`);
  return data?.message?.content || '';
}

async function runGroq(prompt, model = groqModel) {
  const client = new OpenAI({ apiKey: process.env.GROQ_API_KEY, baseURL: 'https://api.groq.com/openai/v1' });
  const response = await client.chat.completions.create({
    model,
    messages: [{ role: 'user', content: prompt }]
  });
  return response.choices?.[0]?.message?.content || '';
}

async function runOpenAI(prompt, webSearch) {
  const client = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
  const tools = webSearch ? [{ type: 'web_search' }] : [];
  const response = await client.responses.create({
    model: process.env.OPENAI_MODEL || 'gpt-5.6-luna',
    input: [{ role: 'user', content: prompt }],
    tools
  });
  return response.output_text || '';
}

app.get('/api/models', async (_req, res) => {
  const models = [];
  if (process.env.GROQ_API_KEY) {
    try {
      const r = await fetch('https://api.groq.com/openai/v1/models', { headers: { Authorization: `Bearer ${process.env.GROQ_API_KEY}` } });
      const d = await r.json();
      for (const m of d?.data || []) models.push({ provider: 'Groq', id: m.id, name: m.id, type: 'chat' });
    } catch (e) { console.error('Groq models failed:', e?.message || e); }
  }
  if (process.env.OPENROUTER_API_KEY) {
    try {
      const r = await fetch('https://openrouter.ai/api/v1/models', { headers: { Authorization: `Bearer ${process.env.OPENROUTER_API_KEY}` } });
      const d = await r.json();
      for (const m of d?.data || []) models.push({ provider: 'OpenRouter', id: m.id, name: m.name || m.id, type: 'chat', context: m.context_length });
    } catch (e) { console.error('OpenRouter models failed:', e?.message || e); }
  }
  if (process.env.GEMINI_API_KEY) {
    try {
      const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${encodeURIComponent(process.env.GEMINI_API_KEY)}`);
      const d = await r.json();
      for (const m of d?.models || []) if ((m.supportedGenerationMethods || []).includes('generateContent')) models.push({ provider: 'Gemini', id: m.name, name: m.displayName || m.name, type: 'chat', context: m.inputTokenLimit });
    } catch (e) { console.error('Gemini models failed:', e?.message || e); }
  }
  res.json({ ok: true, models });
});

app.post('/api/run', async (req, res) => {
  const { agent, task, model, provider } = req.body || {};
  if (!agent?.name || !agent?.instructions || !task?.trim()) {
    return res.status(400).json({ error: 'Agent name, instructions and task are required.' });
  }

  const prompt = promptFor(agent, task);
  const attempts = [];
  if (provider === 'Groq' && process.env.GROQ_API_KEY) attempts.push(['Groq', () => runGroq(prompt, model)]);
  else if (provider === 'OpenRouter' && process.env.OPENROUTER_API_KEY) attempts.push(['OpenRouter', () => runOpenRouter(prompt, agent.webSearch, model)]);
  else if (provider === 'Gemini' && process.env.GEMINI_API_KEY) attempts.push(['Google Gemini', () => runGemini(prompt, model)]);
  else {
    if (process.env.GROQ_API_KEY) attempts.push(['Groq', () => runGroq(prompt, model)]);
    if (process.env.OPENROUTER_API_KEY) attempts.push(['OpenRouter', () => runOpenRouter(prompt, agent.webSearch, model)]);
    if (process.env.GEMINI_API_KEY) attempts.push(['Google Gemini', () => runGemini(prompt, model)]);
  }
  if (process.env.OLLAMA_URL) attempts.push(['Ollama Local', () => runOllama(prompt)]);
  if (process.env.OPENAI_API_KEY) attempts.push(['OpenAI', () => runOpenAI(prompt, agent.webSearch)]);
  if (process.env.GROQ_API_KEY) attempts.push(['Groq', () => runGroq(prompt)]);

  if (!attempts.length) {
    return res.status(503).json({ error: 'לא הוגדר ספק AI. הוסף OPENROUTER_API_KEY או GEMINI_API_KEY, או הגדר OLLAMA_URL למודל מקומי.' });
  }

  const errors = [];
  for (const [name, run] of attempts) {
    try {
      const output = await run();
      if (output) return res.json({ ok: true, output, provider: name, fallback: errors.length > 0 });
    } catch (error) {
      console.error(`${name} failed:`, error?.message || error);
      errors.push(`${name}: ${error?.message || 'failed'}`);
    }
  }

  res.status(502).json({ error: 'כל ספקי ה-AI הזמינים נכשלו.', details: errors });
});

app.get('*splat', (_req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(port, () => console.log(`Agents for Life listening on ${port}`));
