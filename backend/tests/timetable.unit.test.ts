import { normalizeTimetableUpload } from '../src/services/timetableService';

describe('timetable service validation', () => {
  it('normalizes a valid timetable upload', () => {
    const result = normalizeTimetableUpload({
      academicWeekStart: '2026-06-29',
      timezone: 'Asia/Kolkata',
      entries: [
        {
          dayOfWeek: 'monday',
          classroomId: '11111111-2222-4333-8444-555555555555',
          teacherId: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
          courseName: 'Data Structures',
          startTime: '09:00',
          endTime: '10:00'
        }
      ]
    });

    expect(result.academicWeekStart).toBe('2026-06-29');
    expect(result.entries[0].dayOfWeek).toBe('MONDAY');
    expect(result.entries[0].lectureDurationMinutes).toBe(60);
    expect(result.entries[0].joinWindowMinutes).toBe(5);
    expect(result.checksum).toHaveLength(64);
  });

  it('rejects empty uploads', () => {
    expect(() => normalizeTimetableUpload({ academicWeekStart: '2026-06-29', timezone: 'Asia/Kolkata', entries: [] })).toThrow('EMPTY_TIMETABLE_UPLOAD');
  });

  it('rejects invalid day names', () => {
    expect(() =>
      normalizeTimetableUpload({
        academicWeekStart: '2026-06-29',
        timezone: 'Asia/Kolkata',
        entries: [
          {
            dayOfWeek: 'FUNDAY',
            classroomId: '11111111-2222-4333-8444-555555555555',
            teacherId: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
            courseName: 'Data Structures',
            startTime: '09:00',
            endTime: '10:00'
          }
        ]
      })
    ).toThrow('INVALID_DAY_OF_WEEK');
  });

  it('rejects overlapping classroom schedules', () => {
    expect(() =>
      normalizeTimetableUpload({
        academicWeekStart: '2026-06-29',
        timezone: 'Asia/Kolkata',
        entries: [
          {
            dayOfWeek: 'MONDAY',
            classroomId: '11111111-2222-4333-8444-555555555555',
            teacherId: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
            courseName: 'Data Structures',
            startTime: '09:00',
            endTime: '10:00'
          },
          {
            dayOfWeek: 'MONDAY',
            classroomId: '11111111-2222-4333-8444-555555555555',
            teacherId: 'bbbbbbbb-cccc-4ddd-8eee-ffffffffffff',
            courseName: 'Operating Systems',
            startTime: '09:30',
            endTime: '10:30'
          }
        ]
      })
    ).toThrow('CLASSROOM_TIMETABLE_OVERLAP');
  });

  it('rejects overlapping teacher schedules', () => {
    expect(() =>
      normalizeTimetableUpload({
        academicWeekStart: '2026-06-29',
        timezone: 'Asia/Kolkata',
        entries: [
          {
            dayOfWeek: 'MONDAY',
            classroomId: '11111111-2222-4333-8444-555555555555',
            teacherId: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
            courseName: 'Data Structures',
            startTime: '09:00',
            endTime: '10:00'
          },
          {
            dayOfWeek: 'MONDAY',
            classroomId: '99999999-8888-4777-8666-555555555555',
            teacherId: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
            courseName: 'Algorithms',
            startTime: '09:15',
            endTime: '10:15'
          }
        ]
      })
    ).toThrow('TEACHER_TIMETABLE_OVERLAP');
  });

  it('rejects invalid time ranges', () => {
    expect(() =>
      normalizeTimetableUpload({
        academicWeekStart: '2026-06-29',
        timezone: 'Asia/Kolkata',
        entries: [
          {
            dayOfWeek: 'MONDAY',
            classroomId: '11111111-2222-4333-8444-555555555555',
            teacherId: 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
            courseName: 'Data Structures',
            startTime: '10:00',
            endTime: '09:00'
          }
        ]
      })
    ).toThrow('INVALID_TIME_RANGE');
  });
});