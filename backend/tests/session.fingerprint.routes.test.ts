import express from 'express';
import request from 'supertest';
import { signAccessToken } from '../src/auth/jwtService';

jest.mock('../src/config/db', () => {
  const client = {
    query: jest.fn(),
    release: jest.fn()
  };

  return {
    pool: {
      connect: jest.fn(async () => client),
      query: jest.fn()
    }
  };
});

import { pool } from '../src/config/db';
import sessionFingerprintRoutes from '../src/routes/sessionFingerprintRoutes';

const mockedPool = pool as unknown as { connect: jest.Mock; query: jest.Mock };

function makeApp() {
  const app = express();
  app.use(express.json());
  app.use('/sessions', sessionFingerprintRoutes);
  return app;
}

async function createTeacherToken(teacherId: string) {
  return signAccessToken(teacherId, ['TEACHER']);
}

async function createStudentToken(studentId: string) {
  return signAccessToken(studentId, ['STUDENT']);
}

describe('session fingerprint routes', () => {
  beforeEach(() => {
    mockedPool.connect.mockReset();
    mockedPool.query.mockReset();
  });

  it('creates a fingerprint for the active teacher-owned session', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', teacher_id: 'teacher-1', status: 'ACTIVE' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ session_id: 'session-1', teacher_id: 'teacher-1', captured_at: '2026-06-23T10:00:00.000Z', fingerprint_data: [{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Lab', rssi: -50 }], created_at: '2026-06-23T09:00:00.000Z' }] }),
      release: jest.fn()
    };
    mockedPool.connect.mockResolvedValueOnce(client as any);

    const { token } = await createTeacherToken('teacher-1');
    const res = await request(makeApp())
      .post('/sessions/session-1/fingerprint')
      .set('Authorization', `Bearer ${token}`)
      .send({ fingerprint_data: [{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Lab', rssi: -50 }] });

    expect(res.status).toBe(200);
    expect(res.body.session_id).toBe('session-1');
    expect(res.body.teacher_id).toBe('teacher-1');
  });

  it('fetches the stored fingerprint', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', teacher_id: 'teacher-1', status: 'ACTIVE' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ session_id: 'session-1', teacher_id: 'teacher-1', captured_at: '2026-06-23T10:00:00.000Z', fingerprint_data: [{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Lab', rssi: -50 }], created_at: '2026-06-23T09:00:00.000Z' }] }),
      release: jest.fn()
    };
    mockedPool.connect.mockResolvedValueOnce(client as any);

    const { token } = await createTeacherToken('teacher-1');
    const res = await request(makeApp())
      .get('/sessions/session-1/fingerprint')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.session_id).toBe('session-1');
  });

  it('rejects unauthorized ownership access', async () => {
    const client = {
      query: jest.fn().mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', teacher_id: 'teacher-1', status: 'ACTIVE' }] }),
      release: jest.fn()
    };
    mockedPool.connect.mockResolvedValueOnce(client as any);

    const { token } = await createTeacherToken('teacher-2');
    const res = await request(makeApp())
      .get('/sessions/session-1/fingerprint')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(403);
    expect(res.body.error).toBe('SESSION_OWNERSHIP_MISMATCH');
  });

  it('rejects invalid payloads', async () => {
    const client = {
      query: jest.fn().mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', teacher_id: 'teacher-1', status: 'ACTIVE' }] }),
      release: jest.fn()
    };
    mockedPool.connect.mockResolvedValueOnce(client as any);

    const { token } = await createTeacherToken('teacher-1');
    const res = await request(makeApp())
      .post('/sessions/session-1/fingerprint')
      .set('Authorization', `Bearer ${token}`)
      .send({ fingerprint_data: [] });

    expect(res.status).toBe(400);
    expect(res.body.error).toBe('INVALID_FINGERPRINT_PAYLOAD');
  });

  it('rejects non-teacher access', async () => {
    const { token } = await createStudentToken('student-1');
    const res = await request(makeApp())
      .post('/sessions/session-1/fingerprint')
      .set('Authorization', `Bearer ${token}`)
      .send({ fingerprint_data: [{ bssid: 'AA:BB:CC:DD:EE:FF', rssi: -55 }] });

    expect(res.status).toBe(403);
    expect(res.body.error).toBe('insufficient_role');
  });

  it('rejects closed sessions', async () => {
    const client = {
      query: jest.fn().mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', teacher_id: 'teacher-1', status: 'CLOSED' }] }),
      release: jest.fn()
    };
    mockedPool.connect.mockResolvedValueOnce(client as any);

    const { token } = await createTeacherToken('teacher-1');
    const res = await request(makeApp())
      .post('/sessions/session-1/fingerprint')
      .set('Authorization', `Bearer ${token}`)
      .send({ fingerprint_data: [{ bssid: 'AA:BB:CC:DD:EE:FF', rssi: -55 }] });

    expect(res.status).toBe(409);
    expect(res.body.error).toBe('SESSION_NOT_ACTIVE');
  });
});