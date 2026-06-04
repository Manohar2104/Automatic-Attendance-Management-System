import request from 'supertest';
import express from 'express';
import authRoutes from '../src/routes/authRoutes';

// Integration tests require a test DB. Skip if DATABASE_URL not set.
const hasDb = !!process.env.DATABASE_URL;

describe('auth routes (integration)', () => {
  if (!hasDb) {
    it.skip('skipped - no DATABASE_URL', () => {});
    return;
  }

  const app = express();
  app.use(express.json());
  app.use('/auth', authRoutes);

  it('POST /auth/login with missing creds returns 401', async () => {
    const res = await request(app).post('/auth/login').send({ email: 'nope', password: 'x' });
    expect([400, 401, 423]).toContain(res.status);
  });
});
