import express from 'express';
import request from 'supertest';
import { signAccessToken } from '../src/auth/jwtService';

jest.mock('../src/services/lectureMaterializationService', () => ({
  materializeDay: jest.fn(),
  materializeWeek: jest.fn(),
  materializeRange: jest.fn(),
  getLectureMaterializationErrorStatus: jest.fn((error: any) => error?.statusCode || 500),
  getLectureMaterializationErrorMessage: jest.fn((error: any) => error?.message || 'internal_error')
}));

import lectureMaterializationRoutes from '../src/routes/lectureMaterializationRoutes';
import {
  materializeDay,
  materializeWeek,
  materializeRange
} from '../src/services/lectureMaterializationService';

const mockedMaterializeDay = materializeDay as jest.MockedFunction<typeof materializeDay>;
const mockedMaterializeWeek = materializeWeek as jest.MockedFunction<typeof materializeWeek>;
const mockedMaterializeRange = materializeRange as jest.MockedFunction<typeof materializeRange>;

function makeApp() {
  const app = express();
  app.use(express.json());
  app.use('/', lectureMaterializationRoutes);
  return app;
}

async function createInternalToken() {
  return signAccessToken('scheduler-1', ['INTERNAL']);
}

describe('lecture materialization routes', () => {
  beforeEach(() => {
    mockedMaterializeDay.mockReset();
    mockedMaterializeWeek.mockReset();
    mockedMaterializeRange.mockReset();
  });

  it('materializes a single day through the internal materialization endpoint', async () => {
    mockedMaterializeDay.mockResolvedValueOnce({
      academicDate: '2026-06-29',
      generatedCount: 2,
      existingCount: 0,
      lectureInstances: [],
      materializationSource: 'MANUAL_TRIGGER'
    } as any);

    const { token } = await createInternalToken();
    const res = await request(makeApp())
      .post('/internal/materialization/run')
      .set('Authorization', `Bearer ${token}`)
      .send({ academicDate: '2026-06-29' });

    expect(res.status).toBe(201);
    expect(res.body.generatedCount).toBe(2);
  });

  it('materializes a week through the internal materialization endpoint', async () => {
    mockedMaterializeWeek.mockResolvedValueOnce({
      academicWeekStart: '2026-06-29',
      runs: [{ academicDate: '2026-06-29', generatedCount: 2, existingCount: 0, lectureInstances: [], materializationSource: 'MANUAL_TRIGGER' }]
    } as any);

    const { token } = await createInternalToken();
    const res = await request(makeApp())
      .post('/internal/materialization/run')
      .set('Authorization', `Bearer ${token}`)
      .send({ academicWeekStart: '2026-06-29' });

    expect(res.status).toBe(200);
    expect(res.body.generatedCount).toBe(2);
  });

  it('generates lecture instances for selected timetable entry ids', async () => {
    mockedMaterializeRange.mockResolvedValueOnce({
      academicDate: '2026-06-29',
      generatedCount: 2,
      existingCount: 0,
      lectureInstances: [
        {
          lectureInstanceId: 'lecture-1',
          timetableEntryId: 'entry-1',
          sessionId: null,
          lectureDate: '2026-06-29',
          scheduledStartAt: '2026-06-29T03:30:00.000Z',
          scheduledEndAt: '2026-06-29T04:30:00.000Z',
          activationState: 'PENDING',
          activationReason: null
        }
      ]
    } as any);

    const { token } = await createInternalToken();
    const res = await request(makeApp())
      .post('/internal/materialization/run')
      .set('Authorization', `Bearer ${token}`)
      .send({ academicDate: '2026-06-29', timetableEntryIds: ['entry-1', 'entry-2'] });

    expect(res.status).toBe(201);
    expect(res.body.generatedCount).toBe(2);
  });

  it('rejects unauthenticated internal requests', async () => {
    const res = await request(makeApp())
      .post('/internal/sessions/generate')
      .send({ academicDate: '2026-06-29', timetableEntryIds: ['entry-1'] });

    expect(res.status).toBe(401);
  });
});