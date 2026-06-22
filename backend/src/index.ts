import express from 'express';
import { pool } from './config/db';
import { runMigrations } from './scripts/migrate';
import authRoutes from './routes/authRoutes';
import attendanceRoutes from './routes/attendanceRoutes';
import heartbeatRoutes from './routes/heartbeatRoutes';
import fingerprintRoutes from './routes/fingerprintRoutes';
import sessionRoutes from './routes/sessionRoutes';

const app = express();
const port = process.env.PORT ? Number(process.env.PORT) : 3000;

app.use(express.json());

app.use('/auth', authRoutes);
app.use('/attendance', attendanceRoutes);
app.use('/heartbeats', heartbeatRoutes);
app.use('/fingerprints', fingerprintRoutes);
app.use('/sessions', sessionRoutes);

app.get('/health', async (_req, res) => {
  try {
    const client = await pool.connect();
    await client.query('SELECT 1');
    client.release();
    return res.json({ status: 'ok' });
  } catch (err) {
    console.error('DB health check failed', err);
    return res.status(503).json({ status: 'db-unavailable' });
  }
});

app.post('/migrate', async (_req, res) => {
  try {
    await runMigrations();
    return res.json({ migrated: true });
  } catch (err) {
    console.error('Migration error', err);
    return res.status(500).json({ error: 'migration_failed' });
  }
});

app.listen(port, () => {
  console.log(`Backend listening on port ${port}`);
});
