import express from 'express';
import request from 'supertest';
import { signAccessToken } from '../src/auth/jwtService';

jest.mock('../src/services/lectureActivationService', () => ({
  runActivationEngine: jest.fn(),
  evaluateTodayLectures: jest.fn(),
  getTodayLectureInstances: jest.fn(),
  getLectureActivationErrorStatus: jest.fn((error: any) => error?.statusCode || 500),
  getLectureActivationErrorMessage: jest.fn((error: any) => error?.message || 'internal_error')
}));

import lectureActivationRoutes from '../src/routes/lectureActivationRoutes';
import {
  runActivationEngine,
  evaluateTodayLectures,
  getTodayLectureInstances
} from '../src/services/lectureActivationService';

const mockedRunActivationEngine = runActivationEngine as jest.MockedFunction<typeof runActivationEngine>;
const mockedEvaluateTodayLectures = evaluateTodayLectures as jest.MockedFunction<typeof evaluateTodayLectures>;
const mockedGetTodayLectureInstances = getTodayLectureInstances as jest.MockedFunction<typeof getTodayLectureInstances>;

function makeApp() {
  const app = express();
  app.use(express.json());
  app.use('/', lectureActivationRoutes);
  return app;
}

async function createInternalToken() {
  return signAccessToken('scheduler-1', ['INTERNAL']);
}

describe('lecture activation routes', () => {
  beforeEach(() => {
    mockedRunActivationEngine.mockReset();
    mockedEvaluateTodayLectures.mockReset();
    mockedGetTodayLectureInstances.mockReset();
  });

  it('runs the lecture activation engine through the internal endpoint', async () => {
    mockedRunActivationEngine.mockResolvedValueOnce({
      academicDate: '2026-06-29',
      evaluatedAt: '2026-06-29T03:40:00.000Z',
      totalLectureInstances: 1,
      pendingCount: 0,
      activeCount: 1,
      endedCount: 0,
      missedCount: 0,
      lectureInstances: [],
      activatedCount: 1,
      endedLectureCount: 0,
      missedLectureCount: 0
    } as any);

    const { token } = await createInternalToken();
    const res = await request(makeApp())
      .post('/internal/lecture-activation/run')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.activatedCount).toBe(1);
  });

  it('returns lecture activation status through the internal endpoint', async () => {
    mockedEvaluateTodayLectures.mockResolvedValueOnce({
      academicDate: '2026-06-29',
      evaluatedAt: '2026-06-29T03:40:00.000Z',
      totalLectureInstances: 1,
      pendingCount: 1,
      activeCount: 0,
      endedCount: 0,
      missedCount: 0,
      lectureInstances: []
    } as any);
    mockedGetTodayLectureInstances.mockResolvedValueOnce({
      academicDate: '2026-06-29',
      lectureInstances: []
    } as any);

    const { token } = await createInternalToken();
    const res = await request(makeApp())
      .get('/internal/lecture-activation/status')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.pendingCount).toBe(1);
  });

  it('uses the internal lecture activation naming only', async () => {
    mockedEvaluateTodayLectures.mockResolvedValueOnce({
      academicDate: '2026-06-29',
      evaluatedAt: '2026-06-29T03:40:00.000Z',
      totalLectureInstances: 0,
      pendingCount: 0,
      activeCount: 0,
      endedCount: 0,
      missedCount: 0,
      lectureInstances: []
    } as any);
    mockedGetTodayLectureInstances.mockResolvedValueOnce({
      academicDate: '2026-06-29',
      lectureInstances: []
    } as any);

    const { token } = await createInternalToken();
    const res = await request(makeApp())
      .get('/internal/lecture-activation/status')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.engineReady).toBe(true);
  });

  it('rejects unauthenticated activation requests', async () => {
    const res = await request(makeApp()).post('/internal/lecture-activation/run');
    expect(res.status).toBe(401);
  });
});
