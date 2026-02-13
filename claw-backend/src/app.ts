import express from 'express';
import cors from 'cors';
import { authRouter } from './routes/auth.js';
import { instancesRouter } from './routes/instances.js';
import { usageRouter } from './routes/usage.js';
import { configRouter } from './routes/config.js';

export const app = express();

app.use(cors({
  origin: process.env.ALLOWED_ORIGINS?.split(',') || ['http://localhost:3001'],
  credentials: true,
}));

app.use(express.json());

app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

app.use('/api/auth', authRouter);
app.use('/api/instances', instancesRouter);
app.use('/api/instances', configRouter);
app.use('/api/usage', usageRouter);

app.use((err: Error, req: express.Request, res: express.Response, next: express.NextFunction) => {
  console.error('Error:', err.message);
  res.status(500).json({ error: 'Internal server error' });
});
