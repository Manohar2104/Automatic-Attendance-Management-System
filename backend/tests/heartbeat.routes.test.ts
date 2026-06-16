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

jest.mock('../src/services/matchingService', () => ({
  matchFingerprint: jest.fn(async () => ({
    neighbors: [
      { id: 'fingerprint-1', classroom_id: 'class-1', distance: 1, sample_type: 'POSITIVE' }
    ]
  }))
}));

jest.mock('../src/services/confidenceService', () => ({
  computeConfidence: jest.fn(() => ({
    score: 92,
    breakdown: { baseScore: 92 },
    classroom_id: 'class-1'
  }))
}));

import { pool } from '../src/config/db';
import heartbeatRoutes from '../src/routes/heartbeatRoutes';

const mockedPool = pool as unknown as { connect: jest.Mock; query: jest.Mock };

function makeApp() {
  const app = express();
  app.use(express.json());
  app.use('/heartbeats', heartbeatRoutes);
  return app;
}

async function createStudentToken() {
  return signAccessToken('student-1', ['STUDENT']);
}

describe('heartbeat routes', () => {
  beforeEach(() => {
    mockedPool.connect.mockReset();
    mockedPool.query.mockReset();
  });

  it('accepts a valid heartbeat and persists results', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', classroom_id: 'class-1', status: 'ACTIVE' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'attendance-1', session_id: 'session-1', student_id: 'student-1', confidence_score: 70, confidence_breakdown: { runningPresenceScore: 70, acceptedHeartbeats: 0, rejectedHeartbeats: 0 }, status: 'PRESENT' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ device_fingerprint: 'device-1' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'rolling-1', session_id: 'session-1', sequence_number: 1, token_hash: 'hash', valid_from: '2026-06-16T10:00:00.000Z', valid_to: '2026-06-16T10:01:00.000Z' }] })
        .mockResolvedValueOnce({ rowCount: 0, rows: [] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'heartbeat-1', server_ts: '2026-06-16T10:00:30.000Z' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'attendance-1' }] })
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);
    const { token } = await createStudentToken();

    const app = makeApp();
    const res = await request(app)
      .post('/heartbeats')
      .set('Authorization', `Bearer ${token}`)
      .send({
        sessionId: 'session-1',
        wifiFingerprint: [{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Room', rssi: -55 }],
        sequenceNumber: 1,
        deviceFingerprint: 'device-1',
        timestamp: '2026-06-16T10:00:30.000Z'
      });

    expect(res.status).toBe(200);
    expect(res.body.heartbeat).toBeDefined();
    expect(res.body.classificationResult).toBe('INSIDE_CLASSROOM');
    expect(res.body.confidenceScore).toBe(92);
  });

  it('rejects heartbeats for rejected students', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', classroom_id: 'class-1', status: 'ACTIVE' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'attendance-1', session_id: 'session-1', student_id: 'student-1', confidence_score: 0, confidence_breakdown: null, status: 'REJECTED' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ device_fingerprint: 'device-1' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'heartbeat-1', server_ts: '2026-06-16T10:00:30.000Z' }] })
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);
    const { token } = await createStudentToken();

    const app = makeApp();
    const res = await request(app)
      .post('/heartbeats')
      .set('Authorization', `Bearer ${token}`)
      .send({
        sessionId: 'session-1',
        wifiFingerprint: [{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Room', rssi: -55 }],
        sequenceNumber: 1,
        deviceFingerprint: 'device-1',
        timestamp: '2026-06-16T10:00:30.000Z'
      });

    expect(res.status).toBe(403);
    expect(res.body.error).toBe('ATTENDANCE_REJECTED');
  });

  it('rejects inactive sessions', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', classroom_id: 'class-1', status: 'CLOSED' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'attendance-1', session_id: 'session-1', student_id: 'student-1', confidence_score: 0, confidence_breakdown: null, status: 'PRESENT' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ device_fingerprint: 'device-1' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'heartbeat-1', server_ts: '2026-06-16T10:00:30.000Z' }] })
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);
    const { token } = await createStudentToken();

    const app = makeApp();
    const res = await request(app)
      .post('/heartbeats')
      .set('Authorization', `Bearer ${token}`)
      .send({
        sessionId: 'session-1',
        wifiFingerprint: [{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Room', rssi: -55 }],
        sequenceNumber: 1,
        deviceFingerprint: 'device-1',
        timestamp: '2026-06-16T10:00:30.000Z'
      });

    expect(res.status).toBe(409);
    expect(res.body.error).toBe('SESSION_NOT_ACTIVE');
  });

  it('rejects duplicate sequence numbers', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', classroom_id: 'class-1', status: 'ACTIVE' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'attendance-1', session_id: 'session-1', student_id: 'student-1', confidence_score: 70, confidence_breakdown: { runningPresenceScore: 70, acceptedHeartbeats: 1, rejectedHeartbeats: 0 }, status: 'PRESENT' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ device_fingerprint: 'device-1' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'rolling-1', session_id: 'session-1', sequence_number: 2, token_hash: 'hash', valid_from: '2026-06-16T10:00:00.000Z', valid_to: '2026-06-16T10:01:00.000Z' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ seq_no: 2 }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'heartbeat-1', server_ts: '2026-06-16T10:00:30.000Z' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'attendance-1' }] })
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);
    const { token } = await createStudentToken();

    const app = makeApp();
    const res = await request(app)
      .post('/heartbeats')
      .set('Authorization', `Bearer ${token}`)
      .send({
        sessionId: 'session-1',
        wifiFingerprint: [{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Room', rssi: -55 }],
        sequenceNumber: 2,
        deviceFingerprint: 'device-1',
        timestamp: '2026-06-16T10:00:30.000Z'
      });

    expect(res.status).toBe(400);
    expect(res.body.error).toBe('SEQUENCE_OUT_OF_ORDER');
  });

  it('rejects invalid rolling tokens', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', classroom_id: 'class-1', status: 'ACTIVE' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'attendance-1', session_id: 'session-1', student_id: 'student-1', confidence_score: 70, confidence_breakdown: { runningPresenceScore: 70, acceptedHeartbeats: 0, rejectedHeartbeats: 0 }, status: 'PRESENT' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ device_fingerprint: 'device-1' }] })
        .mockResolvedValueOnce({ rowCount: 0, rows: [] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'heartbeat-1', server_ts: '2026-06-16T10:00:30.000Z' }] })
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);
    const { token } = await createStudentToken();

    const app = makeApp();
    const res = await request(app)
      .post('/heartbeats')
      .set('Authorization', `Bearer ${token}`)
      .send({
        sessionId: 'session-1',
        wifiFingerprint: [{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Room', rssi: -55 }],
        sequenceNumber: 1,
        deviceFingerprint: 'device-1',
        timestamp: '2026-06-16T10:00:30.000Z'
      });

    expect(res.status).toBe(401);
    expect(res.body.error).toBe('ROLLING_TOKEN_INVALID');
  });

  it('rejects students without an active device binding', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', classroom_id: 'class-1', status: 'ACTIVE' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'attendance-1', session_id: 'session-1', student_id: 'student-1', confidence_score: 70, confidence_breakdown: { runningPresenceScore: 70, acceptedHeartbeats: 0, rejectedHeartbeats: 0 }, status: 'PRESENT' }] })
        .mockResolvedValueOnce({ rowCount: 0, rows: [] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'heartbeat-1', server_ts: '2026-06-16T10:00:30.000Z' }] })
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);
    const { token } = await createStudentToken();

    const app = makeApp();
    const res = await request(app)
      .post('/heartbeats')
      .set('Authorization', `Bearer ${token}`)
      .send({
        sessionId: 'session-1',
        wifiFingerprint: [{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Room', rssi: -55 }],
        sequenceNumber: 1,
        deviceFingerprint: 'device-1',
        timestamp: '2026-06-16T10:00:30.000Z'
      });

    expect(res.status).toBe(403);
    expect(res.body.error).toBe('NO_DEVICE_BINDING');
  });
});
