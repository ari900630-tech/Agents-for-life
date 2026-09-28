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
const geminiModel = process.env.GEMINI_MODEL || 'gemini-2.5-flash-lite';
const ollamaUrl = (process.env.OLLAMA_URL || 'http://127.0.0.1:11434').replace(/\/$/, '');
const ollamaModel = process.env.OLLAMA_MODEL || 'gemma4';

function providers() {
  return [
    process.env.OPENROUTER_API_KEY && 'OpenRouter Free',
    process.env.GEMINI_API_KEY && 'Google Gemini Free',
    process.env.OLLAMA_URL && 'Ollama Local',
    process.env.OPENAI_API_KEY && 'OpenAI'
  ].filter(Boolean);
}

app.get('/api/health', (_req, res) => {
  res.json({ ok: true, configured: providers().length > 0, providers: providers() });
});

function promptFor(agent, task) {
  return `You are the agent named "${agent.name}".\nYour role and instructions:\n${agent.instructions}\n\nExecute the user's task directly. Be concrete, concise, and action-oriented. If a requested action requires an external account, credential, approval, or human confirmation that you do not have, clearly identify the missing step instead of pretending it was completed. Never claim an external action happened unless an available tool actually completed it.\n\nUser task:\n${task.trim()}`;
}

async function runOpenRouter(prompt, webSearch) {
  const client = new OpenAI({ apiKey: process.env.OPENROUTER_API_KEY, baseURL: 'https://openrouter.ai/api/v1' });
  const response = await client.chat.completions.create({
    model: openRouterModel,
    messages: [{ role: 'user', content: prompt }],
    ...(webSearch ? {} : {})
  });
  return response.choices?.[0]?.message?.content || '';
}

async function runGemini(prompt) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(geminiModel)}:generateContent?key=${encodeURIComponent(process.env.GEMINI_API_KEY)}`;
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

app.post('/api/run', async (req, res) => {
  const { agent, task } = req.body || {};
  if (!agent?.name || !agent?.instructions || !task?.trim()) {
    return res.status(400).json({ error: 'Agent name, instructions and task are required.' });
  }

  const prompt = promptFor(agent, task);
  const attempts = [];
  if (process.env.OPENROUTER_API_KEY) attempts.push(['OpenRouter Free', () => runOpenRouter(prompt, agent.webSearch)]);
  if (process.env.GEMINI_API_KEY) attempts.push(['Google Gemini Free', () => runGemini(prompt)]);
  if (process.env.OLLAMA_URL) attempts.push(['Ollama Local', () => runOllama(prompt)]);
  if (process.env.OPENAI_API_KEY) attempts.push(['OpenAI', () => runOpenAI(prompt, agent.webSearch)]);

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
