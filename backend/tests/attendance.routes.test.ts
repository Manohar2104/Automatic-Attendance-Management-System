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
import attendanceRoutes from '../src/routes/attendanceRoutes';

const mockedPool = pool as unknown as { connect: jest.Mock; query: jest.Mock };

function makeApp() {
  const app = express();
  app.use(express.json());
  app.use('/attendance', attendanceRoutes);
  return app;
}

async function createStudentToken() {
  return signAccessToken('student-1', ['STUDENT']);
}

describe('attendance routes', () => {
  beforeEach(() => {
    mockedPool.query.mockReset();
  });

  it('returns attendance history for the authenticated student', async () => {
    mockedPool.query.mockResolvedValueOnce({
      rowCount: 1,
      rows: [{
        session_id: 'session-1',
        course_name: 'Mathematics',
        status: 'PRESENT',
        join_score: 100,
        join_time: '2026-06-16T10:00:00.000Z',
        confidence_breakdown: { acceptedHeartbeats: 4, lastHeartbeatSequence: 4, lastHeartbeatStatus: 'ACCEPTED', lastHeartbeatTime: '2026-06-16T10:45:00.000Z' }
      }]
    });

    const { token } = await createStudentToken();
    const res = await request(makeApp())
      .get('/attendance/history')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.records).toHaveLength(1);
    expect(res.body.records[0].courseName).toBe('Mathematics');
    expect(res.body.records[0].joinScore).toBe(100);
  });

  it('returns current attendance summary for the authenticated student', async () => {
    mockedPool.query.mockResolvedValueOnce({
      rowCount: 1,
      rows: [{
        session_id: 'session-1',
        course_name: 'Mathematics',
        status: 'PRESENT',
        join_score: 100,
        join_time: '2026-06-16T10:00:00.000Z',
        confidence_breakdown: { acceptedHeartbeats: 3, lastHeartbeatSequence: 3, lastHeartbeatStatus: 'ACCEPTED', lastHeartbeatTime: '2026-06-16T10:45:00.000Z' },
        current_heartbeat_status: 'ACCEPTED',
        current_heartbeat_sequence_number: 3,
        current_heartbeat_timestamp: '2026-06-16T10:45:00.000Z'
      }]
    });

    const { token } = await createStudentToken();
    const res = await request(makeApp())
      .get('/attendance/current')
      .query({ sessionId: 'session-1' })
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.record.sessionId).toBe('session-1');
    expect(res.body.record.totalAcceptedHeartbeats).toBe(3);
    expect(res.body.record.confidenceStatus).toBe('Awaiting teacher fingerprint validation');
  });
});