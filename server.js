import express from 'express';
import OpenAI from 'openai';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
app.use(express.json({ limit: '2mb' }));
app.use(express.static(path.join(__dirname, 'public')));

const port = process.env.PORT || 3000;
const model = process.env.OPENAI_MODEL || 'gpt-5.6-luna';

app.get('/api/health', (_req, res) => {
  res.json({ ok: true, configured: Boolean(process.env.OPENAI_API_KEY), model });
});

app.post('/api/run', async (req, res) => {
  try {
    if (!process.env.OPENAI_API_KEY) {
      return res.status(503).json({ error: 'OPENAI_API_KEY is not configured on the server.' });
    }

    const { agent, task } = req.body || {};
    if (!agent?.name || !agent?.instructions || !task?.trim()) {
      return res.status(400).json({ error: 'Agent name, instructions and task are required.' });
    }

    const client = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
    const tools = [];
    if (agent.webSearch) tools.push({ type: 'web_search' });

    const system = `You are the agent named "${agent.name}".\nYour role and instructions:\n${agent.instructions}\n\nExecute the user's task directly. Be concrete, concise, and action-oriented. If a requested action requires an external account, credential, approval, or human confirmation that you do not have, clearly identify the missing step instead of pretending it was completed. Never claim an external action happened unless the available tools actually completed it.`;

    const response = await client.responses.create({
      model,
      input: [
        { role: 'system', content: system },
        { role: 'user', content: task.trim() }
      ],
      tools
    });

    res.json({ ok: true, output: response.output_text || 'הסוכן לא החזיר טקסט.' });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: error?.message || 'Agent execution failed.' });
  }
});

app.get('*splat', (_req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(port, () => console.log(`Agents for Life listening on ${port}`));
