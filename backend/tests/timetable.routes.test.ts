import express from 'express';
import request from 'supertest';
import { signAccessToken } from '../src/auth/jwtService';

jest.mock('../src/services/timetableService', () => ({
  uploadTimetable: jest.fn(),
  listTimetables: jest.fn(),
  getTimetable: jest.fn(),
  updateTimetable: jest.fn(),
  deleteTimetable: jest.fn(),
  getTimetableErrorStatus: jest.fn((error: any) => error?.statusCode || 500),
  getTimetableErrorMessage: jest.fn((error: any) => error?.message || 'internal_error')
}));

import timetableRoutes from '../src/routes/timetableRoutes';
import {
  deleteTimetable,
  getTimetable,
  listTimetables,
  updateTimetable,
  uploadTimetable
} from '../src/services/timetableService';

const mockedUploadTimetable = uploadTimetable as jest.MockedFunction<typeof uploadTimetable>;
const mockedListTimetables = listTimetables as jest.MockedFunction<typeof listTimetables>;
const mockedGetTimetable = getTimetable as jest.MockedFunction<typeof getTimetable>;
const mockedUpdateTimetable = updateTimetable as jest.MockedFunction<typeof updateTimetable>;
const mockedDeleteTimetable = deleteTimetable as jest.MockedFunction<typeof deleteTimetable>;

function makeApp() {
  const app = express();
  app.use(express.json());
  app.use('/', timetableRoutes);
  return app;
}

async function createTeacherToken() {
  return signAccessToken('teacher-1', ['TEACHER']);
}

async function createAdminToken() {
  return signAccessToken('admin-1', ['ADMIN']);
}

describe('timetable routes', () => {
  beforeEach(() => {
    mockedUploadTimetable.mockReset();
    mockedListTimetables.mockReset();
    mockedGetTimetable.mockReset();
    mockedUpdateTimetable.mockReset();
    mockedDeleteTimetable.mockReset();
  });

  it('uploads a timetable successfully', async () => {
    mockedUploadTimetable.mockResolvedValueOnce({
      timetableId: 'timetable-1',
      academicWeekStart: '2026-06-29',
      status: 'UPLOADED',
      entryCount: 1,
      uploadedBy: 'teacher-1',
      uploadedAt: '2026-06-29T00:00:00.000Z',
      sourceFilename: 'weekly-timetable.json',
      checksum: 'abc'
    } as any);

    const { token } = await createTeacherToken();
    const res = await request(makeApp())
      .post('/timetables/weekly')
      .set('Authorization', `Bearer ${token}`)
      .send({
        academicWeekStart: '2026-06-29',
        timezone: 'Asia/Kolkata',
        entries: [
          {
            dayOfWeek: 'MONDAY',
            classroomId: '11111111-2222-3333-4444-555555555555',
            teacherId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
            courseName: 'Data Structures',
            startTime: '09:00',
            endTime: '10:00'
          }
        ]
      });

    expect(res.status).toBe(201);
    expect(res.body.timetableId).toBe('timetable-1');
    expect(res.body.entryCount).toBe(1);
  });

  it('rejects duplicate timetable uploads', async () => {
    mockedUploadTimetable.mockRejectedValueOnce(Object.assign(new Error('DUPLICATE_TIMETABLE_WEEK'), { statusCode: 409 }));

    const { token } = await createTeacherToken();
    const res = await request(makeApp())
      .post('/timetables/weekly')
      .set('Authorization', `Bearer ${token}`)
      .send({
        academicWeekStart: '2026-06-29',
        timezone: 'Asia/Kolkata',
        entries: [
          {
            dayOfWeek: 'MONDAY',
            classroomId: '11111111-2222-3333-4444-555555555555',
            teacherId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
            courseName: 'Data Structures',
            startTime: '09:00',
            endTime: '10:00'
          }
        ]
      });

    expect(res.status).toBe(409);
    expect(res.body.error).toBe('DUPLICATE_TIMETABLE_WEEK');
  });

  it('lists timetables', async () => {
    mockedListTimetables.mockResolvedValueOnce({
      items: [
        {
          timetableId: 'timetable-1',
          academicWeekStart: '2026-06-29',
          status: 'UPLOADED',
          entryCount: 1,
          uploadedBy: 'teacher-1',
          uploadedAt: '2026-06-29T00:00:00.000Z',
          sourceFilename: 'weekly-timetable.json',
          checksum: 'abc'
        }
      ]
    } as any);

    const { token } = await createTeacherToken();
    const res = await request(makeApp())
      .get('/timetables')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.items).toHaveLength(1);
  });

  it('gets a timetable by id', async () => {
    mockedGetTimetable.mockResolvedValueOnce({
      timetable: {
        timetableId: 'timetable-1',
        academicWeekStart: '2026-06-29',
        status: 'UPLOADED',
        entryCount: 1,
        uploadedBy: 'teacher-1',
        uploadedAt: '2026-06-29T00:00:00.000Z',
        sourceFilename: 'weekly-timetable.json',
        checksum: 'abc',
        entries: []
      }
    } as any);

    const { token } = await createTeacherToken();
    const res = await request(makeApp())
      .get('/timetables/timetable-1')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.timetable.timetableId).toBe('timetable-1');
  });

  it('updates a timetable', async () => {
    mockedUpdateTimetable.mockResolvedValueOnce({ timetableId: 'timetable-1', status: 'UPDATED', entryCount: 1 } as any);

    const { token } = await createTeacherToken();
    const res = await request(makeApp())
      .patch('/timetables/timetable-1')
      .set('Authorization', `Bearer ${token}`)
      .send({
        academicWeekStart: '2026-06-29',
        timezone: 'Asia/Kolkata',
        entries: [
          {
            entryId: '11111111-2222-3333-4444-555555555555',
            dayOfWeek: 'MONDAY',
            classroomId: '11111111-2222-3333-4444-555555555555',
            teacherId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
            courseName: 'Algorithms',
            startTime: '09:30',
            endTime: '10:30'
          }
        ]
      });

    expect(res.status).toBe(200);
    expect(res.body.status).toBe('UPDATED');
  });

  it('deletes a timetable', async () => {
    mockedDeleteTimetable.mockResolvedValueOnce({ deleted: true, timetableId: 'timetable-1' } as any);

    const { token } = await createTeacherToken();
    const res = await request(makeApp())
      .delete('/timetables/timetable-1')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.deleted).toBe(true);
  });

  it('lists timetables for admin on the admin route', async () => {
    mockedListTimetables.mockResolvedValueOnce({ items: [] } as any);

    const { token } = await createAdminToken();
    const res = await request(makeApp())
      .get('/admin/timetables')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.items).toEqual([]);
  });
});