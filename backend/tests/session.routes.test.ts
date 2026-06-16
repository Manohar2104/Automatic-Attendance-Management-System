import express from 'express';
import request from 'supertest';

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
import sessionRoutes from '../src/routes/sessionRoutes';

const mockedPool = pool as unknown as { connect: jest.Mock; query: jest.Mock };

function makeApp() {
  const app = express();
  app.use(express.json());
  app.use('/sessions', sessionRoutes);
  return app;
}

// ============================================================================
// STUDENT-FACING APIs — Phase 5 Focus
// ============================================================================

describe('session routes — Student App (Phase 5)', () => {
  beforeEach(() => {
    mockedPool.connect.mockReset();
    mockedPool.query.mockReset();
  });

  it('lists active sessions for student to browse', async () => {
    mockedPool.query.mockResolvedValueOnce({
      rows: [{ id: 'session-1', classroom_id: 'class-1', teacher_id: 'teacher-1', course_name: 'Math', status: 'ACTIVE', start_time: '2026-06-16T10:00:00.000Z', end_time: null, presence_threshold_present: 85, presence_threshold_partial: 60, join_window_minutes: 10, created_at: '2026-06-16T10:00:00.000Z' }]
    });

    const app = makeApp();
    const res = await request(app).get('/sessions/active');

    expect(res.status).toBe(200);
    expect(res.body.sessions).toBeDefined();
    expect(res.body.sessions.length).toBe(1);
  });

  it('allows student to join a session with automatic join score', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', status: 'ACTIVE', classroom_id: 'class-1', teacher_id: 'teacher-1', course_name: 'Math', start_time: '2026-06-16T10:00:00.000Z', end_time: null, presence_threshold_present: 85, presence_threshold_partial: 60, join_window_minutes: 10, created_at: '2026-06-16T10:00:00.000Z' }] })
        .mockResolvedValueOnce({ rowCount: 0, rows: [] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'attendance-1' }] })
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);

    const app = makeApp();
    const res = await request(app).post('/sessions/session-1/join').send({
      studentId: 'student-1',
      joinedAt: '2026-06-16T10:01:30.000Z'
    });

    expect(res.status).toBe(200);
    expect(res.body.joinScore).toBe(100);
    expect(res.body.attendanceStatus).toBe('PRESENT');
    expect(res.body.sessionId).toBe('session-1');
  });

  it('assigns REJECTED status for late joins (>5 minutes)', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', status: 'ACTIVE', classroom_id: 'class-1', teacher_id: 'teacher-1', course_name: 'Math', start_time: '2026-06-16T10:00:00.000Z', end_time: null, presence_threshold_present: 85, presence_threshold_partial: 60, join_window_minutes: 10, created_at: '2026-06-16T10:00:00.000Z' }] })
        .mockResolvedValueOnce({ rowCount: 0, rows: [] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'attendance-1' }] })
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);

    const app = makeApp();
    const res = await request(app).post('/sessions/session-1/join').send({
      studentId: 'student-1',
      joinedAt: '2026-06-16T10:05:30.000Z'
    });

    expect(res.status).toBe(200);
    expect(res.body.joinScore).toBe(0);
    expect(res.body.attendanceStatus).toBe('REJECTED');
  });
});

// ============================================================================
// DEFERRED APIs — Teacher Dashboard (Phase 6+)
// ============================================================================

describe('session routes — Deferred Teacher APIs', () => {
  it('returns 501 for deferred POST /sessions/start', async () => {
    const app = makeApp();
    const res = await request(app).post('/sessions/start').send({ teacherId: 'teacher-1', classroomId: 'class-1', courseName: 'Math' });

    expect(res.status).toBe(501);
    expect(res.body.error).toBe('DEFERRED_FOR_TEACHER_DASHBOARD');
  });

  it('returns 501 for deferred POST /sessions/:id/end', async () => {
    const app = makeApp();
    const res = await request(app).post('/sessions/session-1/end').send();

    expect(res.status).toBe(501);
    expect(res.body.error).toBe('DEFERRED_FOR_TEACHER_DASHBOARD');
  });

  it('returns 501 for deferred GET /sessions/:id', async () => {
    const app = makeApp();
    const res = await request(app).get('/sessions/session-1');

    expect(res.status).toBe(501);
    expect(res.body.error).toBe('DEFERRED_FOR_REVIEW');
  });
});
