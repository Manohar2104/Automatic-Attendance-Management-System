jest.mock('../src/config/db', () => {
  const query = jest.fn();
  return { pool: { query } };
});

import { pool } from '../src/config/db';
import {
  getDailyRegistrationStatus,
  getTodayDailyRegistration,
  registerDailyRegistration,
  getCurrentAcademicDate
} from '../src/services/dailyRegistrationService';
import { validateRegisteredDevice } from '../src/auth/deviceService';

const mockedPool = pool as unknown as { query: jest.Mock };

describe('daily registration service', () => {
  beforeEach(() => {
    mockedPool.query.mockReset();
  });

  it('registers a student for the day', async () => {
    mockedPool.query
      .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'student-1' }] })
      .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'binding-1', device_fingerprint: 'device-hash' }] })
      .mockResolvedValueOnce({ rowCount: 0, rows: [] })
      .mockResolvedValueOnce({
        rowCount: 1,
        rows: [{
          id: 'registration-1',
          student_id: 'student-1',
          academic_date: '2026-06-29',
          registered_at: '2026-06-29T08:00:00.000Z',
          device_binding_id: 'binding-1',
          created_at: '2026-06-29T08:00:00.000Z',
          updated_at: '2026-06-29T08:00:00.000Z'
        }]
      });

    const result = await registerDailyRegistration({
      studentId: 'student-1',
    });

    expect(result.registrationId).toBe('registration-1');
    expect(result.registered).toBe(true);
  });

  it('rejects duplicate registrations for the same academic day', async () => {
    mockedPool.query
      .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'student-1' }] })
      .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'binding-1', device_fingerprint: 'device-hash' }] })
      .mockResolvedValueOnce({
        rowCount: 1,
        rows: [{
          id: 'registration-1',
          student_id: 'student-1',
          academic_date: '2026-06-29',
          registered_at: '2026-06-29T08:00:00.000Z',
          device_binding_id: 'binding-1',
          created_at: '2026-06-29T08:00:00.000Z',
          updated_at: '2026-06-29T08:00:00.000Z'
        }]
      });

    await expect(registerDailyRegistration({
      studentId: 'student-1',
    })).rejects.toMatchObject({ message: 'DAILY_REGISTRATION_ALREADY_EXISTS', statusCode: 409 });
  });

  it('rejects an invalid device binding', async () => {
    mockedPool.query
      .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'student-1' }] })
      .mockResolvedValueOnce({ rowCount: 0, rows: [] });

    await expect(registerDailyRegistration({
      studentId: 'student-1',
    })).rejects.toMatchObject({ message: 'NO_DEVICE_BINDING', statusCode: 403 });
  });

  it('allows a next-day registration after a previous day registration', async () => {
    mockedPool.query
      .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'student-1' }] })
      .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'binding-1', device_fingerprint: 'device-hash' }] })
      .mockResolvedValueOnce({ rowCount: 0, rows: [] })
      .mockResolvedValueOnce({
        rowCount: 1,
        rows: [{
          id: 'registration-1',
          student_id: 'student-1',
          academic_date: '2026-06-29',
          registered_at: '2026-06-29T08:00:00.000Z',
          device_binding_id: 'binding-1',
          created_at: '2026-06-29T08:00:00.000Z',
          updated_at: '2026-06-29T08:00:00.000Z'
        }]
      })
      .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'student-1' }] })
      .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'binding-1', device_fingerprint: 'device-hash' }] })
      .mockResolvedValueOnce({ rowCount: 0, rows: [] })
      .mockResolvedValueOnce({
        rowCount: 1,
        rows: [{
          id: 'registration-2',
          student_id: 'student-1',
          academic_date: '2026-06-30',
          registered_at: '2026-06-30T08:00:00.000Z',
          device_binding_id: 'binding-1',
          created_at: '2026-06-30T08:00:00.000Z',
          updated_at: '2026-06-30T08:00:00.000Z'
        }]
      });

    const first = await registerDailyRegistration({
      studentId: 'student-1',
    });

    const second = await registerDailyRegistration({
      studentId: 'student-1',
    });

    expect(first.registrationId).toBe('registration-1');
    expect(second.registrationId).toBe('registration-2');
  });

  it('returns today registration lookup details', async () => {
    mockedPool.query.mockResolvedValueOnce({
      rowCount: 1,
      rows: [{
        id: 'registration-1',
        student_id: 'student-1',
        academic_date: getCurrentAcademicDate(new Date('2026-06-29T18:00:00.000Z')),
        registered_at: '2026-06-29T08:00:00.000Z',
        device_binding_id: 'binding-1',
        created_at: '2026-06-29T08:00:00.000Z',
        updated_at: '2026-06-29T08:00:00.000Z'
      }]
    });

    const result = await getTodayDailyRegistration('student-1');
    expect(result?.registrationId).toBe('registration-1');
    expect(result?.registered).toBe(true);
  });

  it('returns registration status for today', async () => {
    mockedPool.query.mockResolvedValueOnce({
      rowCount: 1,
      rows: [{
        id: 'registration-1',
        student_id: 'student-1',
        academic_date: getCurrentAcademicDate(new Date('2026-06-29T18:00:00.000Z')),
        registered_at: '2026-06-29T08:00:00.000Z',
        device_binding_id: 'binding-1',
        created_at: '2026-06-29T08:00:00.000Z',
        updated_at: '2026-06-29T08:00:00.000Z'
      }]
    });

    const result = await getDailyRegistrationStatus('student-1');
    expect(result).toEqual({
      academicDate: getCurrentAcademicDate(new Date('2026-06-29T18:00:00.000Z')),
      registrationId: 'registration-1',
      registered: true
    });
  });

  it('uses the existing device binding service abstraction', async () => {
    mockedPool.query.mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'binding-1', device_fingerprint: 'device-hash' }] });

    const binding = await validateRegisteredDevice('student-1');
    expect(binding?.id).toBe('binding-1');
  });
});