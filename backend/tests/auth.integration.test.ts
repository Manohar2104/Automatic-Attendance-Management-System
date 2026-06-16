
jest.setTimeout(30000);
import 'dotenv/config';
import request from 'supertest';
import express from 'express';
import { pool } from '../src/config/db';
import authRoutes from '../src/routes/authRoutes';
import { hashPassword } from '../src/auth/passwordService';

console.log('Reached assertion');

const hasDb = !!process.env.DATABASE_URL;

describe('Student Authentication Integration (Phase 6)', () => {
  if (!hasDb) {
    it.skip('skipped - no DATABASE_URL', () => {});
    return;
  }

  const app = express();
  app.use(express.json());
  app.use('/auth', authRoutes);

  let testUserId: string;
  let testEmail: string;
  let testPassword: string;

  beforeAll(async () => {
    // Seed test user
    testEmail = `test-${Date.now()}@example.com`;
    testPassword = 'TestPassword123!';
    const hash = await hashPassword(testPassword);
    const r = await pool.query(
      `INSERT INTO users (email, password_hash, full_name) VALUES ($1, $2, $3) RETURNING id`,
      [testEmail, hash, 'Test Student']
    );
    testUserId = r.rows[0].id;
  });

  afterAll(async () => {
    // Clean up test user and related records
    if (testUserId) {
      await pool.query(`DELETE FROM security_events WHERE user_id = $1`, [testUserId]);
      await pool.query(`DELETE FROM refresh_tokens WHERE user_id = $1`, [testUserId]);
      await pool.query(`DELETE FROM device_bindings WHERE user_id = $1`, [testUserId]);
      await pool.query(`DELETE FROM account_locks WHERE user_id = $1`, [testUserId]);
      await pool.query(`DELETE FROM users WHERE id = $1`, [testUserId]);
    }
  });

  // ========================================================================
  // LOGIN FLOW TESTS
  // ========================================================================

  describe('POST /auth/login', () => {
    it('successful login returns access token and refresh token', async () => {
      const res = await request(app).post('/auth/login').send({
        email: testEmail,
        password: testPassword,
        deviceName: 'TestDevice'
      });

      expect(res.status).toBe(200);
      expect(res.body.accessToken).toBeDefined();
      expect(res.body.refreshToken).toBeDefined();
      expect(res.body.deviceId).toBeDefined();
    });

    it('login with invalid credentials returns 401', async () => {
      const res = await request(app).post('/auth/login').send({
        email: testEmail,
        password: 'WrongPassword123!'
      });

      expect(res.status).toBe(401);
      expect(res.body.error).toBe('invalid_credentials');
    });

    it('login with non-existent user returns 401', async () => {
      const res = await request(app).post('/auth/login').send({
        email: 'nonexistent@example.com',
        password: 'SomePassword123!'
      });

      expect(res.status).toBe(401);
      expect(res.body.error).toBe('invalid_credentials');
    });

    it('account lockout after multiple failed attempts', async () => {
      const lockoutEmail = `lockout-${Date.now()}@example.com`;
      const lockoutPassword = 'LockoutTestPass123!';
      const hash = await hashPassword(lockoutPassword);
      const r = await pool.query(
        `INSERT INTO users (email, password_hash, full_name) VALUES ($1, $2, $3) RETURNING id`,
        [lockoutEmail, hash, 'Lockout Test']
      );
      const userId = r.rows[0].id;

      try {
        // Trigger 5 failed attempts
        for (let i = 0; i < 5; i++) {
          const res = await request(app).post('/auth/login').send({
            email: lockoutEmail,
            password: 'WrongPassword'
          });
          expect(res.status).toBe(401);
        }

        // Next attempt should be locked (423)
        const res = await request(app).post('/auth/login').send({
          email: lockoutEmail,
          password: lockoutPassword
        });

        expect(res.status).toBe(423);
        expect(res.body.error).toBe('account_locked');
      } finally {
        await pool.query(`DELETE FROM account_locks WHERE user_id = $1`, [userId]);
        await pool.query(`DELETE FROM users WHERE id = $1`, [userId]);
      }
    });
  });

  // ========================================================================
  // TOKEN REFRESH TESTS
  // ========================================================================

  describe('POST /auth/refresh', () => {
    it('refresh with valid token returns new access and refresh token', async () => {
      const loginRes = await request(app).post('/auth/login').send({
        email: testEmail,
        password: testPassword
      });
      const oldRefresh = loginRes.body.refreshToken;

      const refreshRes = await request(app).post('/auth/refresh').send({
        refreshToken: oldRefresh
      });

      expect(refreshRes.status).toBe(200);
      expect(refreshRes.body.accessToken).toBeDefined();
      expect(refreshRes.body.refreshToken).toBeDefined();
      expect(refreshRes.body.refreshToken).not.toBe(oldRefresh); // should be rotated
    });

    it('refresh without token returns 400', async () => {
      const res = await request(app).post('/auth/refresh').send({});

      expect(res.status).toBe(400);
      expect(res.body.error).toBe('missing_refresh');
    });

    it('refresh with invalid token returns 401', async () => {
      const res = await request(app).post('/auth/refresh').send({
        refreshToken: 'invalid_token_xxx'
      });

      expect(res.status).toBe(401);
      expect(res.body.error).toBe('invalid_refresh');
    });

    it('reuse of revoked/expired token triggers theft detection', async () => {
      const loginRes = await request(app).post('/auth/login').send({
        email: testEmail,
        password: testPassword
      });
      const refresh1 = loginRes.body.refreshToken;

      // First rotation is valid
      const rotate1 = await request(app).post('/auth/refresh').send({
        refreshToken: refresh1
      });
      expect(rotate1.status).toBe(200);

      // Reuse of old token should trigger theft detection
      const reuseRes = await request(app).post('/auth/refresh').send({
        refreshToken: refresh1
      });
      expect(reuseRes.status).toBe(401);
      expect(reuseRes.body.error).toBe('invalid_refresh');
    });
  });

  // ========================================================================
  // LOGOUT TESTS
  // ========================================================================

  describe('POST /auth/logout', () => {
    it('logout revokes refresh token', async () => {
      const loginRes = await request(app).post('/auth/login').send({
        email: testEmail,
        password: testPassword
      });
      const refreshToken = loginRes.body.refreshToken;

      const logoutRes = await request(app).post('/auth/logout').send({
        refreshToken
      });

      expect(logoutRes.status).toBe(200);
      expect(logoutRes.body.loggedOut).toBe(true);

      // Token should no longer be usable
      const refreshRes = await request(app).post('/auth/refresh').send({
        refreshToken
      });
      expect(refreshRes.status).toBe(401);
    });

    it('logout without token returns 400', async () => {
      const res = await request(app).post('/auth/logout').send({});

      expect(res.status).toBe(400);
      expect(res.body.error).toBe('missing_refresh');
    });

    it('logout with invalid token returns 200 (idempotent)', async () => {
      const res = await request(app).post('/auth/logout').send({
        refreshToken: 'invalid_token'
      });

      expect(res.status).toBe(200);
      expect(res.body.loggedOut).toBe(true);
    });
  });

  // ========================================================================
  // STUDENT PROFILE TESTS
  // ========================================================================

  describe('GET /auth/me', () => {
    it('returns authenticated student profile', async () => {
      const loginRes = await request(app).post('/auth/login').send({
        email: testEmail,
        password: testPassword
      });
      const accessToken = loginRes.body.accessToken;

      const meRes = await request(app)
        .get('/auth/me')
        .set('Authorization', `Bearer ${accessToken}`);

      expect(meRes.status).toBe(200);
      expect(meRes.body.user).toBeDefined();
      expect(meRes.body.user.id).toBe(testUserId);
      expect(meRes.body.user.email).toBe(testEmail);
      expect(meRes.body.user.full_name).toBe('Test Student');
    });

    it('returns 401 without authorization header', async () => {
      const res = await request(app).get('/auth/me');

      expect(res.status).toBe(401);
      expect(res.body.error).toBe('missing_token');
    });

    it('returns 401 with invalid token', async () => {
      const res = await request(app)
        .get('/auth/me')
        .set('Authorization', 'Bearer invalid_token');

      expect(res.status).toBe(401);
      expect(res.body.error).toBe('invalid_token');
    });

    it('returns 401 with expired token', async () => {
      const loginRes = await request(app).post('/auth/login').send({
        email: testEmail,
        password: testPassword
      });
      // Simulate expired token by using a modified/invalid one
      const fakeExpiredToken = loginRes.body.accessToken.slice(0, -5) + 'xxxxx';

      const res = await request(app)
        .get('/auth/me')
        .set('Authorization', `Bearer ${fakeExpiredToken}`);

      expect(res.status).toBe(401);
    });
  });

  // ========================================================================
  // DEVICE REGISTRATION TESTS
  // ========================================================================

  describe('Device binding on login', () => {
    it('creates device binding on first login', async () => {
      const deviceEmail = `device-${Date.now()}@example.com`;
      const devicePassword = 'DevicePass123!';
      const hash = await hashPassword(devicePassword);
      const r = await pool.query(
        `INSERT INTO users (email, password_hash, full_name) VALUES ($1, $2, $3) RETURNING id`,
        [deviceEmail, hash, 'Device Test']
      );
      const deviceUserId = r.rows[0].id;

      try {
        const loginRes = await request(app).post('/auth/login').send({
          email: deviceEmail,
          password: devicePassword,
          deviceName: 'iPhone 14'
        });

        expect(loginRes.status).toBe(200);
        expect(loginRes.body.deviceId).toBeDefined();

        // Verify device binding was created
        const deviceRes = await pool.query(
          `SELECT * FROM device_bindings WHERE user_id = $1`,
          [deviceUserId]
        );
        expect(deviceRes.rowCount).toBeGreaterThan(0);
        expect(deviceRes.rows[0].name).toBe('iPhone 14');
      } finally {
        await pool.query(`DELETE FROM device_bindings WHERE user_id = $1`, [deviceUserId]);
        await pool.query(`DELETE FROM refresh_tokens WHERE user_id = $1`, [deviceUserId]);
        await pool.query(`DELETE FROM users WHERE id = $1`, [deviceUserId]);
      }
    });

    it('login without device name skips device binding', async () => {
      const noDeviceEmail = `nodevice-${Date.now()}@example.com`;
      const noDevicePassword = 'NoDevicePass123!';
      const hash = await hashPassword(noDevicePassword);
      const r = await pool.query(
        `INSERT INTO users (email, password_hash, full_name) VALUES ($1, $2, $3) RETURNING id`,
        [noDeviceEmail, hash, 'No Device Test']
      );
      const noDeviceUserId = r.rows[0].id;

      try {
        const loginRes = await request(app).post('/auth/login').send({
          email: noDeviceEmail,
          password: noDevicePassword
        });

        expect(loginRes.status).toBe(200);
        expect(loginRes.body.deviceId).toBeNull();
      } finally {
        await pool.query(`DELETE FROM refresh_tokens WHERE user_id = $1`, [noDeviceUserId]);
        await pool.query(`DELETE FROM users WHERE id = $1`, [noDeviceUserId]);
      }
    });
  });

  // ========================================================================
  // COMPLETE WORKFLOW TEST
  // ========================================================================

  describe('Complete student session workflow', () => {
    it('login -> refresh -> logout workflow succeeds', async () => {
      const workflowEmail = `workflow-${Date.now()}@example.com`;
      const workflowPassword = 'WorkflowPass123!';
      const hash = await hashPassword(workflowPassword);
      const r = await pool.query(
        `INSERT INTO users (email, password_hash, full_name) VALUES ($1, $2, $3) RETURNING id`,
        [workflowEmail, hash, 'Workflow Test']
      );
      const workflowUserId = r.rows[0].id;

      try {
        // Step 1: Login
        const loginRes = await request(app).post('/auth/login').send({
          email: workflowEmail,
          password: workflowPassword,
          deviceName: 'TestDevice'
        });
        expect(loginRes.status).toBe(200);
        const accessToken = loginRes.body.accessToken;
        const refreshToken = loginRes.body.refreshToken;

        // Step 2: Verify profile
        const meRes = await request(app)
          .get('/auth/me')
          .set('Authorization', `Bearer ${accessToken}`);
        expect(meRes.status).toBe(200);
        expect(meRes.body.user.email).toBe(workflowEmail);

        // Step 3: Refresh token
        const refreshRes = await request(app).post('/auth/refresh').send({
          refreshToken
        });
        expect(refreshRes.status).toBe(200);
        const newAccessToken = refreshRes.body.accessToken;
        const newRefreshToken = refreshRes.body.refreshToken;

        // Step 4: Verify profile with new token
        const meRes2 = await request(app)
          .get('/auth/me')
          .set('Authorization', `Bearer ${newAccessToken}`);
        expect(meRes2.status).toBe(200);

        // Step 5: Logout
        const logoutRes = await request(app).post('/auth/logout').send({
          refreshToken: newRefreshToken
        });
        expect(logoutRes.status).toBe(200);

        // Step 6: Verify refresh token is revoked
        const badRefreshRes = await request(app).post('/auth/refresh').send({
          refreshToken: newRefreshToken
        });
        expect(badRefreshRes.status).toBe(401);
      } finally {
        await pool.query(`DELETE FROM security_events WHERE user_id = $1`, [workflowUserId]);
        await pool.query(`DELETE FROM device_bindings WHERE user_id = $1`, [workflowUserId]);
        await pool.query(`DELETE FROM refresh_tokens WHERE user_id = $1`, [workflowUserId]);
        await pool.query(`DELETE FROM users WHERE id = $1`, [workflowUserId]);
      }
    });
  });
});




afterAll(async () => {
  await pool.end();
});