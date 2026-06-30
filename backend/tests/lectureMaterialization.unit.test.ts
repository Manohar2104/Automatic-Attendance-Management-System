jest.mock('../src/config/db', () => {
  const query = jest.fn();
  const client = { query: jest.fn(), release: jest.fn() };
  return {
    pool: {
      query,
      connect: jest.fn(async () => client)
    }
  };
});

import { pool } from '../src/config/db';
import {
  getLectureInstance,
  listLectureInstances,
  materializeLectureInstancesForAcademicDate,
  materializeLectureInstancesForEntryIds
} from '../src/services/lectureMaterializationService';

const mockedPool = pool as unknown as { query: jest.Mock; connect: jest.Mock };

function buildClient(responses: Array<unknown>) {
  const query = jest.fn();
  for (const response of responses) {
    query.mockResolvedValueOnce(response);
  }
  return {
    query,
    release: jest.fn()
  };
}

describe('lecture materialization service', () => {
  beforeEach(() => {
    mockedPool.query.mockReset();
    mockedPool.connect.mockReset();
  });

  it('materializes a lecture day from timetable entries', async () => {
    const client = buildClient([
      {},
      { rowCount: 1, rows: [{ id: 'upload-1', uploaded_by: 'teacher-1', academic_week_start: '2026-06-29', status: 'UPLOADED' }] },
      {
        rowCount: 2,
        rows: [
          {
            id: 'entry-1',
            timetable_upload_id: 'upload-1',
            day_of_week: 'MONDAY',
            classroom_id: 'class-1',
            teacher_id: 'teacher-1',
            course_name: 'Networks',
            start_time_local: '09:00',
            end_time_local: '10:00',
            lecture_duration_minutes: 60,
            join_window_minutes: 5,
            active_flag: true,
            created_at: '2026-06-29T00:00:00.000Z'
          },
          {
            id: 'entry-2',
            timetable_upload_id: 'upload-1',
            day_of_week: 'MONDAY',
            classroom_id: 'class-2',
            teacher_id: 'teacher-2',
            course_name: 'Databases',
            start_time_local: '10:00',
            end_time_local: '11:00',
            lecture_duration_minutes: 60,
            join_window_minutes: 5,
            active_flag: true,
            created_at: '2026-06-29T00:00:00.000Z'
          }
        ]
      },
      {
        rowCount: 1,
        rows: [{
          id: 'lecture-1',
          timetable_entry_id: 'entry-1',
          session_id: null,
          lecture_date: '2026-06-29',
          scheduled_start_at: '2026-06-29T03:30:00.000Z',
          scheduled_end_at: '2026-06-29T04:30:00.000Z',
          activation_state: 'PENDING',
          activation_reason: null,
          created_at: '2026-06-29T00:00:00.000Z'
        }]
      },
      {
        rowCount: 1,
        rows: [{
          id: 'lecture-2',
          timetable_entry_id: 'entry-2',
          session_id: null,
          lecture_date: '2026-06-29',
          scheduled_start_at: '2026-06-29T04:30:00.000Z',
          scheduled_end_at: '2026-06-29T05:30:00.000Z',
          activation_state: 'PENDING',
          activation_reason: null,
          created_at: '2026-06-29T00:00:00.000Z'
        }]
      },
      {
        rowCount: 2,
        rows: [
          {
            id: 'lecture-1',
            timetable_entry_id: 'entry-1',
            session_id: null,
            lecture_date: '2026-06-29',
            scheduled_start_at: '2026-06-29T03:30:00.000Z',
            scheduled_end_at: '2026-06-29T04:30:00.000Z',
            activation_state: 'PENDING',
            activation_reason: null,
            created_at: '2026-06-29T00:00:00.000Z'
          },
          {
            id: 'lecture-2',
            timetable_entry_id: 'entry-2',
            session_id: null,
            lecture_date: '2026-06-29',
            scheduled_start_at: '2026-06-29T04:30:00.000Z',
            scheduled_end_at: '2026-06-29T05:30:00.000Z',
            activation_state: 'PENDING',
            activation_reason: null,
            created_at: '2026-06-29T00:00:00.000Z'
          }
        ]
      },
      {}
    ]);

    mockedPool.connect.mockResolvedValueOnce(client as any);

    const result = await materializeLectureInstancesForAcademicDate('2026-06-29');
    expect(result.generatedCount).toBe(2);
    expect(result.lectureInstances).toHaveLength(2);
    expect(result.lectureInstances[0].activationState).toBe('PENDING');
  });

  it('prevents duplicate materialization by returning existing lecture instances', async () => {
    const firstClient = buildClient([
      {},
      { rowCount: 1, rows: [{ id: 'upload-1', uploaded_by: 'teacher-1', academic_week_start: '2026-06-29', status: 'UPLOADED' }] },
      {
        rowCount: 1,
        rows: [{
          id: 'entry-1',
          timetable_upload_id: 'upload-1',
          day_of_week: 'MONDAY',
          classroom_id: 'class-1',
          teacher_id: 'teacher-1',
          course_name: 'Networks',
          start_time_local: '09:00',
          end_time_local: '10:00',
          lecture_duration_minutes: 60,
          join_window_minutes: 5,
          active_flag: true,
          created_at: '2026-06-29T00:00:00.000Z'
        }]
      },
      {
        rowCount: 1,
        rows: [{
          id: 'lecture-1',
          timetable_entry_id: 'entry-1',
          session_id: null,
          lecture_date: '2026-06-29',
          scheduled_start_at: '2026-06-29T03:30:00.000Z',
          scheduled_end_at: '2026-06-29T04:30:00.000Z',
          activation_state: 'PENDING',
          activation_reason: null,
          created_at: '2026-06-29T00:00:00.000Z'
        }]
      },
      {
        rowCount: 1,
        rows: [{
          id: 'lecture-1',
          timetable_entry_id: 'entry-1',
          session_id: null,
          lecture_date: '2026-06-29',
          scheduled_start_at: '2026-06-29T03:30:00.000Z',
          scheduled_end_at: '2026-06-29T04:30:00.000Z',
          activation_state: 'PENDING',
          activation_reason: null,
          created_at: '2026-06-29T00:00:00.000Z'
        }]
      },
      {}
    ]);

    const secondClient = buildClient([
      {},
      { rowCount: 1, rows: [{ id: 'upload-1', uploaded_by: 'teacher-1', academic_week_start: '2026-06-29', status: 'UPLOADED' }] },
      {
        rowCount: 1,
        rows: [{
          id: 'entry-1',
          timetable_upload_id: 'upload-1',
          day_of_week: 'MONDAY',
          classroom_id: 'class-1',
          teacher_id: 'teacher-1',
          course_name: 'Networks',
          start_time_local: '09:00',
          end_time_local: '10:00',
          lecture_duration_minutes: 60,
          join_window_minutes: 5,
          active_flag: true,
          created_at: '2026-06-29T00:00:00.000Z'
        }]
      },
      { rowCount: 0, rows: [] },
      {
        rowCount: 1,
        rows: [{
          id: 'lecture-1',
          timetable_entry_id: 'entry-1',
          session_id: null,
          lecture_date: '2026-06-29',
          scheduled_start_at: '2026-06-29T03:30:00.000Z',
          scheduled_end_at: '2026-06-29T04:30:00.000Z',
          activation_state: 'PENDING',
          activation_reason: null,
          created_at: '2026-06-29T00:00:00.000Z'
        }]
      },
      {}
    ]);

    mockedPool.connect
      .mockResolvedValueOnce(firstClient as any)
      .mockResolvedValueOnce(secondClient as any);

    const first = await materializeLectureInstancesForAcademicDate('2026-06-29');
    const second = await materializeLectureInstancesForAcademicDate('2026-06-29');

    expect(first.generatedCount).toBe(1);
    expect(second.generatedCount).toBe(0);
    expect(second.lectureInstances).toHaveLength(1);
  });

  it('rejects invalid timetable data', async () => {
    const client = buildClient([
      {},
      { rowCount: 0, rows: [] },
      {}
    ]);

    mockedPool.connect.mockResolvedValueOnce(client as any);

    await expect(materializeLectureInstancesForAcademicDate('2026-06-29')).rejects.toMatchObject({ message: 'TIMETABLE_NOT_FOUND', statusCode: 404 });
  });

  it('rejects empty timetable windows', async () => {
    const client = buildClient([
      {},
      { rowCount: 1, rows: [{ id: 'upload-1', uploaded_by: 'teacher-1', academic_week_start: '2026-06-29', status: 'UPLOADED' }] },
      { rowCount: 0, rows: [] },
      {}
    ]);

    mockedPool.connect.mockResolvedValueOnce(client as any);

    await expect(materializeLectureInstancesForAcademicDate('2026-06-30')).rejects.toMatchObject({ message: 'EMPTY_TIMETABLE', statusCode: 404 });
  });

  it('materializes a selected list of timetable entries', async () => {
    const client = buildClient([
      {},
      {
        rowCount: 2,
        rows: [
          {
            id: 'entry-1',
            timetable_upload_id: 'upload-1',
            day_of_week: 'MONDAY',
            classroom_id: 'class-1',
            teacher_id: 'teacher-1',
            course_name: 'Networks',
            start_time_local: '09:00',
            end_time_local: '10:00',
            lecture_duration_minutes: 60,
            join_window_minutes: 5,
            active_flag: true,
            created_at: '2026-06-29T00:00:00.000Z'
          },
          {
            id: 'entry-2',
            timetable_upload_id: 'upload-1',
            day_of_week: 'MONDAY',
            classroom_id: 'class-2',
            teacher_id: 'teacher-2',
            course_name: 'Databases',
            start_time_local: '10:00',
            end_time_local: '11:00',
            lecture_duration_minutes: 60,
            join_window_minutes: 5,
            active_flag: true,
            created_at: '2026-06-29T00:00:00.000Z'
          }
        ]
      },
      {
        rowCount: 1,
        rows: [{
          id: 'lecture-1',
          timetable_entry_id: 'entry-1',
          session_id: null,
          lecture_date: '2026-06-29',
          scheduled_start_at: '2026-06-29T03:30:00.000Z',
          scheduled_end_at: '2026-06-29T04:30:00.000Z',
          activation_state: 'PENDING',
          activation_reason: null,
          created_at: '2026-06-29T00:00:00.000Z'
        }]
      },
      {
        rowCount: 1,
        rows: [{
          id: 'lecture-2',
          timetable_entry_id: 'entry-2',
          session_id: null,
          lecture_date: '2026-06-29',
          scheduled_start_at: '2026-06-29T04:30:00.000Z',
          scheduled_end_at: '2026-06-29T05:30:00.000Z',
          activation_state: 'PENDING',
          activation_reason: null,
          created_at: '2026-06-29T00:00:00.000Z'
        }]
      },
      {
        rowCount: 2,
        rows: [
          {
            id: 'lecture-1',
            timetable_entry_id: 'entry-1',
            session_id: null,
            lecture_date: '2026-06-29',
            scheduled_start_at: '2026-06-29T03:30:00.000Z',
            scheduled_end_at: '2026-06-29T04:30:00.000Z',
            activation_state: 'PENDING',
            activation_reason: null,
            created_at: '2026-06-29T00:00:00.000Z'
          },
          {
            id: 'lecture-2',
            timetable_entry_id: 'entry-2',
            session_id: null,
            lecture_date: '2026-06-29',
            scheduled_start_at: '2026-06-29T04:30:00.000Z',
            scheduled_end_at: '2026-06-29T05:30:00.000Z',
            activation_state: 'PENDING',
            activation_reason: null,
            created_at: '2026-06-29T00:00:00.000Z'
          }
        ]
      },
      {}
    ]);
    mockedPool.connect.mockResolvedValueOnce(client as any);

    const result = await materializeLectureInstancesForEntryIds('2026-06-29', ['entry-1', 'entry-2']);
    expect(result.generatedCount).toBe(2);
    expect(result.lectureInstances).toHaveLength(2);
  });

  it('reads lecture instances by id and list', async () => {
    mockedPool.query.mockResolvedValueOnce({ rowCount: 1, rows: [{
      id: 'lecture-1',
      timetable_entry_id: 'entry-1',
      session_id: null,
      lecture_date: '2026-06-29',
      scheduled_start_at: '2026-06-29T03:30:00.000Z',
      scheduled_end_at: '2026-06-29T04:30:00.000Z',
      activation_state: 'PENDING',
      activation_reason: null,
      created_at: '2026-06-29T00:00:00.000Z'
    }] });

    mockedPool.query.mockResolvedValueOnce({ rowCount: 1, rows: [{
      id: 'lecture-1',
      timetable_entry_id: 'entry-1',
      session_id: null,
      lecture_date: '2026-06-29',
      scheduled_start_at: '2026-06-29T03:30:00.000Z',
      scheduled_end_at: '2026-06-29T04:30:00.000Z',
      activation_state: 'PENDING',
      activation_reason: null,
      created_at: '2026-06-29T00:00:00.000Z'
    }] });

    const single = await getLectureInstance('lecture-1');
    const list = await listLectureInstances({ academicDate: '2026-06-29' });

    expect(single?.lectureInstanceId).toBe('lecture-1');
    expect(list.lectureInstances).toHaveLength(1);
  });
});
