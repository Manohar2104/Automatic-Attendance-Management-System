import express from 'express';
import request from 'supertest';
import { signAccessToken } from '../src/auth/jwtService';

jest.mock('../src/services/dailyRegistrationService', () => ({
  registerDailyRegistration: jest.fn(),
  getDailyRegistrationStatus: jest.fn(),
  getDailyRegistrationErrorStatus: jest.fn((error: any) => error?.statusCode || 500),
  getDailyRegistrationErrorMessage: jest.fn((error: any) => error?.message || 'internal_error')
}));

import dailyRegistrationRoutes from '../src/routes/dailyRegistrationRoutes';
import {
  getDailyRegistrationStatus,
  registerDailyRegistration
} from '../src/services/dailyRegistrationService';

const mockedRegisterDailyRegistration = registerDailyRegistration as jest.MockedFunction<typeof registerDailyRegistration>;
const mockedGetDailyRegistrationStatus = getDailyRegistrationStatus as jest.MockedFunction<typeof getDailyRegistrationStatus>;

function makeApp() {
  const app = express();
  app.use(express.json());
  app.use('/', dailyRegistrationRoutes);
  return app;
}

async function createStudentToken() {
  return signAccessToken('student-1', ['STUDENT']);
}

describe('daily registration routes', () => {
  beforeEach(() => {
    mockedRegisterDailyRegistration.mockReset();
    mockedGetDailyRegistrationStatus.mockReset();
  });

  it('creates a daily registration', async () => {
    mockedRegisterDailyRegistration.mockResolvedValueOnce({
      registrationId: 'registration-1',
      studentId: 'student-1',
      academicDate: '2026-06-29',
      registeredAt: '2026-06-29T08:00:00.000Z',
      deviceBindingId: 'binding-1',
      registered: true
    } as any);

    const { token } = await createStudentToken();
    const res = await request(makeApp())
      .post('/daily-registration')
      .set('Authorization', `Bearer ${token}`)
      .send({
        mode: 'AUTO_MONITORING'
      });

    expect(res.status).toBe(201);
    expect(res.body.registrationId).toBe('registration-1');
    expect(res.body.registered).toBe(true);
  });

  it('rejects duplicate registrations', async () => {
    mockedRegisterDailyRegistration.mockRejectedValueOnce(Object.assign(new Error('DAILY_REGISTRATION_ALREADY_EXISTS'), { statusCode: 409 }));

    const { token } = await createStudentToken();
    const res = await request(makeApp())
      .post('/daily-registration')
      .set('Authorization', `Bearer ${token}`)
      .send({
      });

    expect(res.status).toBe(409);
    expect(res.body.error).toBe('DAILY_REGISTRATION_ALREADY_EXISTS');
  });

  it('rejects invalid devices', async () => {
    mockedRegisterDailyRegistration.mockRejectedValueOnce(Object.assign(new Error('NO_DEVICE_BINDING'), { statusCode: 403 }));

    const { token } = await createStudentToken();
    const res = await request(makeApp())
      .post('/daily-registration')
      .set('Authorization', `Bearer ${token}`)
      .send({
      });

    expect(res.status).toBe(403);
    expect(res.body.error).toBe('NO_DEVICE_BINDING');
  });

  it('rejects unauthenticated requests', async () => {
    const res = await request(makeApp())
      .post('/daily-registration')
      .send({
      });

    expect(res.status).toBe(401);
  });

  it('returns today registration lookup', async () => {
    mockedGetDailyRegistrationStatus.mockResolvedValueOnce({
      registrationId: 'registration-1',
      studentId: 'student-1',
      academicDate: '2026-06-29',
      registeredAt: '2026-06-29T08:00:00.000Z',
      deviceBindingId: 'binding-1',
      registered: true
    } as any);

    const { token } = await createStudentToken();
    const res = await request(makeApp())
      .get('/daily-registration')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.registrationId).toBe('registration-1');
    expect(res.body.registered).toBe(true);
  });

  it('returns registration status', async () => {
    mockedGetDailyRegistrationStatus.mockResolvedValueOnce({
      academicDate: '2026-06-29',
      registrationId: 'registration-1',
      registered: true
    } as any);

    const { token } = await createStudentToken();
    const res = await request(makeApp())
      .get('/daily-registration/status')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.registrationId).toBe('registration-1');
    expect(res.body.registered).toBe(true);
  });
});