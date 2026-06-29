import { pool } from '../config/db';
import type { PoolClient } from 'pg';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const;
const DEFAULT_JOIN_WINDOW_MINUTES = 5;

export type TimetableEntryInput = {
  entryId?: string;
  dayOfWeek?: string;
  classroomId?: string;
  teacherId?: string;
  courseName?: string;
  courseCode?: string;
  lectureCode?: string;
  startTime?: string;
  endTime?: string;
  joinWindowMinutes?: number | string;
};

export type TimetableUploadInput = {
  academicWeekStart?: string;
  timezone?: string;
  sourceFilename?: string;
  entries?: TimetableEntryInput[];
};

export type NormalizedTimetableUpload = {
  academicWeekStart: string;
  timezone: string;
  sourceFilename: string;
  checksum: string;
  entries: Array<{
    entryId: string;
    dayOfWeek: (typeof DAYS)[number];
    classroomId: string;
    teacherId: string;
    courseName: string;
    startTime: string;
    endTime: string;
    lectureDurationMinutes: number;
    joinWindowMinutes: number;
    activeFlag: boolean;
  }>;
};

export type TimetableActor = {
  userId: string;
  roles?: string[];
};

type UploadRow = {
  id: string;
  uploaded_by: string;
  academic_week_start: string;
  source_filename: string;
  checksum: string;
  uploaded_at: string;
  status: string;
};

type EntryRow = {
  id: string;
  timetable_upload_id: string;
  day_of_week: string;
  classroom_id: string;
  teacher_id: string;
  course_name: string;
  start_time_local: string;
  end_time_local: string;
  lecture_duration_minutes: number;
  join_window_minutes: number;
  active_flag: boolean;
  created_at: string;
};

function makeError(message: string, statusCode: number) {
  const error = new Error(message) as Error & { statusCode?: number };
  error.statusCode = statusCode;
  return error;
}

function trim(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizeDate(value: unknown) {
  const date = trim(value);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    throw makeError('INVALID_ACADEMIC_WEEK_START', 400);
  }

  const parsed = new Date(`${date}T00:00:00.000Z`);
  if (Number.isNaN(parsed.getTime())) {
    throw makeError('INVALID_ACADEMIC_WEEK_START', 400);
  }

  return date;
}

function normalizeTimezone(value: unknown) {
  const timezone = trim(value);
  if (!timezone) {
    return 'UTC';
  }

  try {
    new Intl.DateTimeFormat('en-US', { timeZone: timezone });
    return timezone;
  } catch (_error) {
    throw makeError('INVALID_TIMEZONE', 400);
  }
}

function normalizeDay(value: unknown) {
  const day = trim(value).toUpperCase();
  if (!DAYS.includes(day as (typeof DAYS)[number])) {
    throw makeError('INVALID_DAY_OF_WEEK', 400);
  }

  return day as (typeof DAYS)[number];
}

function normalizeTime(value: unknown, field: 'startTime' | 'endTime') {
  const time = trim(value);
  const match = /^(\d{2}):(\d{2})$/.exec(time);
  if (!match) {
    throw makeError(`INVALID_${field.toUpperCase()}`, 400);
  }

  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  if (!Number.isInteger(hours) || !Number.isInteger(minutes) || hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
    throw makeError(`INVALID_${field.toUpperCase()}`, 400);
  }

  return { time, minutesFromMidnight: hours * 60 + minutes };
}

function normalizeJoinWindow(value: unknown) {
  if (value === undefined || value === null || value === '') {
    return DEFAULT_JOIN_WINDOW_MINUTES;
  }

  const parsed = typeof value === 'number' ? value : Number(value);
  if (!Number.isInteger(parsed) || parsed < 5 || parsed > 15) {
    throw makeError('INVALID_JOIN_WINDOW_MINUTES', 400);
  }

  return parsed;
}

function trimUuid(value: unknown, fieldName: string) {
  const result = trim(value);
  if (!result) {
    throw makeError(`MISSING_${fieldName.toUpperCase()}`, 400);
  }

  if (!/^([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})$/i.test(result)) {
    throw makeError(`INVALID_${fieldName.toUpperCase()}`, 404);
  }

  return result;
}

function chooseCourseName(entry: TimetableEntryInput) {
  const courseName = trim(entry.courseName) || trim(entry.courseCode) || trim(entry.lectureCode);
  if (!courseName) {
    throw makeError('MISSING_COURSE_NAME', 400);
  }

  return courseName;
}

function normalizeEntry(entry: TimetableEntryInput) {
  if (!entry || typeof entry !== 'object') {
    throw makeError('INVALID_TIMETABLE_ENTRY', 400);
  }

  const classroomId = trimUuid(entry.classroomId, 'classroom_id');
  const teacherId = trimUuid(entry.teacherId, 'teacher_id');
  const dayOfWeek = normalizeDay(entry.dayOfWeek);
  const start = normalizeTime(entry.startTime, 'startTime');
  const end = normalizeTime(entry.endTime, 'endTime');

  if (end.minutesFromMidnight <= start.minutesFromMidnight) {
    throw makeError('INVALID_TIME_RANGE', 400);
  }

  return {
    entryId: trim(entry.entryId),
    dayOfWeek,
    classroomId,
    teacherId,
    courseName: chooseCourseName(entry),
    startTime: start.time,
    endTime: end.time,
    lectureDurationMinutes: end.minutesFromMidnight - start.minutesFromMidnight,
    joinWindowMinutes: normalizeJoinWindow(entry.joinWindowMinutes),
    activeFlag: true
  };
}

export function normalizeTimetableUpload(input: TimetableUploadInput) {
  const academicWeekStart = normalizeDate(input.academicWeekStart);
  const timezone = normalizeTimezone(input.timezone);
  const sourceFilename = trim(input.sourceFilename) || 'weekly-timetable.json';
  const entries = Array.isArray(input.entries) ? input.entries : [];

  if (entries.length === 0) {
    throw makeError('EMPTY_TIMETABLE_UPLOAD', 400);
  }

  const normalizedEntries = entries.map(normalizeEntry);
  const entryIds = normalizedEntries.map((entry) => entry.entryId).filter(Boolean);
  if (new Set(entryIds).size !== entryIds.length) {
    throw makeError('DUPLICATE_ENTRY_ID', 400);
  }

  for (let leftIndex = 0; leftIndex < normalizedEntries.length; leftIndex += 1) {
    for (let rightIndex = leftIndex + 1; rightIndex < normalizedEntries.length; rightIndex += 1) {
      const left = normalizedEntries[leftIndex];
      const right = normalizedEntries[rightIndex];
      if (left.dayOfWeek !== right.dayOfWeek) {
        continue;
      }

      const leftStart = timeToMinutes(left.startTime);
      const leftEnd = timeToMinutes(left.endTime);
      const rightStart = timeToMinutes(right.startTime);
      const rightEnd = timeToMinutes(right.endTime);
      const overlaps = leftStart < rightEnd && rightStart < leftEnd;

      if (!overlaps) {
        continue;
      }

      if (left.classroomId === right.classroomId) {
        throw makeError('CLASSROOM_TIMETABLE_OVERLAP', 400);
      }

      if (left.teacherId === right.teacherId) {
        throw makeError('TEACHER_TIMETABLE_OVERLAP', 400);
      }
    }
  }

  const checksum = createChecksum({ academicWeekStart, timezone, sourceFilename, entries: normalizedEntries });

  return { academicWeekStart, timezone, sourceFilename, checksum, entries: normalizedEntries };
}

function createChecksum(payload: unknown) {
  const hash = require('crypto').createHash('sha256');
  hash.update(JSON.stringify(payload));
  return hash.digest('hex');
}

function timeToMinutes(value: string) {
  const match = /^(\d{2}):(\d{2})$/.exec(value);
  if (!match) {
    throw makeError('INVALID_TIME_RANGE', 400);
  }

  return Number(match[1]) * 60 + Number(match[2]);
}

async function runInTransaction<T>(work: (client: PoolClient) => Promise<T>) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await work(client);
    await client.query('COMMIT');
    return result;
  } catch (error) {
    try {
      await client.query('ROLLBACK');
    } catch (_rollbackError) {
      // keep original failure
    }
    throw error;
  } finally {
    client.release();
  }
}

async function assertReferencedRowsExist(client: PoolClient, entries: NormalizedTimetableUpload['entries']) {
  const teacherIds = [...new Set(entries.map((entry) => entry.teacherId))];
  const classroomIds = [...new Set(entries.map((entry) => entry.classroomId))];

  const teacherResult = await client.query<{ id: string }>(`SELECT id FROM teachers WHERE id = ANY($1::uuid[])`, [teacherIds]);
  const classroomResult = await client.query<{ id: string }>(`SELECT id FROM classrooms WHERE id = ANY($1::uuid[])`, [classroomIds]);

  if (teacherResult.rowCount !== teacherIds.length) {
    throw makeError('TEACHER_NOT_FOUND', 404);
  }

  if (classroomResult.rowCount !== classroomIds.length) {
    throw makeError('CLASSROOM_NOT_FOUND', 404);
  }
}

async function assertNoDuplicateUpload(client: PoolClient, academicWeekStart: string, checksum: string) {
  const weekResult = await client.query(`SELECT 1 FROM timetable_uploads WHERE academic_week_start = $1::date AND status <> 'DELETED' LIMIT 1`, [academicWeekStart]);
  if ((weekResult.rowCount ?? 0) > 0) {
    throw makeError('DUPLICATE_TIMETABLE_WEEK', 409);
  }

  const checksumResult = await client.query(`SELECT 1 FROM timetable_uploads WHERE checksum = $1 LIMIT 1`, [checksum]);
  if ((checksumResult.rowCount ?? 0) > 0) {
    throw makeError('DUPLICATE_TIMETABLE_UPLOAD', 409);
  }
}

function rowToSummary(row: UploadRow, entryCount: number) {
  return {
    timetableId: row.id,
    academicWeekStart: row.academic_week_start,
    status: row.status,
    entryCount,
    uploadedBy: row.uploaded_by,
    uploadedAt: row.uploaded_at,
    sourceFilename: row.source_filename,
    checksum: row.checksum
  };
}

function rowToEntry(row: EntryRow) {
  return {
    entryId: row.id,
    timetableEntryId: row.id,
    dayOfWeek: row.day_of_week,
    classroomId: row.classroom_id,
    teacherId: row.teacher_id,
    courseName: row.course_name,
    startTime: row.start_time_local,
    endTime: row.end_time_local,
    lectureDurationMinutes: row.lecture_duration_minutes,
    joinWindowMinutes: row.join_window_minutes,
    activeFlag: row.active_flag,
    createdAt: row.created_at
  };
}

async function loadUpload(client: PoolClient, timetableId: string) {
  const result = await client.query<UploadRow>(
    `SELECT id, uploaded_by, academic_week_start, source_filename, checksum, uploaded_at, status
       FROM timetable_uploads
      WHERE id = $1
      LIMIT 1`,
    [timetableId]
  );

  return result.rowCount ? result.rows[0] : null;
}

async function loadEntries(client: PoolClient, timetableId: string) {
  const result = await client.query<EntryRow>(
    `SELECT id, timetable_upload_id, day_of_week, classroom_id, teacher_id, course_name, start_time_local, end_time_local, lecture_duration_minutes, join_window_minutes, active_flag, created_at
       FROM timetable_entries
      WHERE timetable_upload_id = $1
      ORDER BY day_of_week, start_time_local, id`,
    [timetableId]
  );

  return result.rows;
}

function assertActor(actorId: string) {
  const trimmed = trim(actorId);
  if (!trimmed) {
    throw makeError('MISSING_REQUIRED_FIELDS', 400);
  }

  return trimmed;
}

export async function uploadTimetable(actorId: string, payload: TimetableUploadInput) {
  const userId = assertActor(actorId);
  const normalized = normalizeTimetableUpload(payload);

  return runInTransaction(async (client) => {
    await assertReferencedRowsExist(client, normalized.entries);
    await assertNoDuplicateUpload(client, normalized.academicWeekStart, normalized.checksum);

    const uploadResult = await client.query<UploadRow>(
      `INSERT INTO timetable_uploads (uploaded_by, academic_week_start, source_filename, checksum, status)
       VALUES ($1, $2::date, $3, $4, 'UPLOADED')
       RETURNING id, uploaded_by, academic_week_start, source_filename, checksum, uploaded_at, status`,
      [userId, normalized.academicWeekStart, normalized.sourceFilename, normalized.checksum]
    );

    const upload = uploadResult.rows[0];

    for (const entry of normalized.entries) {
      await client.query(
        `INSERT INTO timetable_entries (
           id, timetable_upload_id, day_of_week, classroom_id, teacher_id, course_name,
           start_time_local, end_time_local, lecture_duration_minutes, join_window_minutes, active_flag
         ) VALUES ($1::uuid, $2, $3, $4::uuid, $5::uuid, $6, $7::time, $8::time, $9, $10, $11)`,
        [entry.entryId || null, upload.id, entry.dayOfWeek, entry.classroomId, entry.teacherId, entry.courseName, entry.startTime, entry.endTime, entry.lectureDurationMinutes, entry.joinWindowMinutes, entry.activeFlag]
      );
    }

    return rowToSummary(upload, normalized.entries.length);
  });
}

export async function listTimetables(filters: { academicWeekStart?: string; status?: string } = {}) {
  const conditions = [`t.status <> 'DELETED'`];
  const values: Array<string> = [];

  if (filters.academicWeekStart) {
    values.push(normalizeDate(filters.academicWeekStart));
    conditions.push(`t.academic_week_start = $${values.length}::date`);
  }

  if (filters.status) {
    values.push(trim(filters.status));
    conditions.push(`t.status = $${values.length}`);
  }

  const result = await pool.query(
    `SELECT t.id, t.uploaded_by, t.academic_week_start, t.source_filename, t.checksum, t.uploaded_at, t.status, COUNT(e.id)::int AS entry_count
       FROM timetable_uploads t
       LEFT JOIN timetable_entries e ON e.timetable_upload_id = t.id
      WHERE ${conditions.join(' AND ')}
      GROUP BY t.id
      ORDER BY t.academic_week_start DESC, t.uploaded_at DESC`,
    values
  );

  return { items: result.rows.map((row) => rowToSummary(row as UploadRow, Number((row as any).entry_count ?? 0))) };
}

export async function getTimetable(timetableId: string, actorId: string, isAdmin = false) {
  const userId = assertActor(actorId);
  const id = assertActor(timetableId);

  return runInTransaction(async (client) => {
    const upload = await loadUpload(client, id);
    if (!upload || upload.status === 'DELETED') {
      throw makeError('TIMETABLE_NOT_FOUND', 404);
    }

    if (!isAdmin && upload.uploaded_by !== userId) {
      throw makeError('TIMETABLE_OWNERSHIP_MISMATCH', 403);
    }

    const entries = await loadEntries(client, id);
    return {
      timetable: {
        ...rowToSummary(upload, entries.length),
        entries: entries.map(rowToEntry)
      }
    };
  });
}

export async function updateTimetable(timetableId: string, actorId: string, payload: TimetableUploadInput, isAdmin = false) {
  const userId = assertActor(actorId);
  const id = assertActor(timetableId);
  const normalized = normalizeTimetableUpload(payload);

  if (!normalized.entries.every((entry) => entry.entryId)) {
    throw makeError('MISSING_REQUIRED_FIELDS', 400);
  }

  return runInTransaction(async (client) => {
    const upload = await loadUpload(client, id);
    if (!upload || upload.status === 'DELETED') {
      throw makeError('TIMETABLE_NOT_FOUND', 404);
    }

    if (!isAdmin && upload.uploaded_by !== userId) {
      throw makeError('TIMETABLE_OWNERSHIP_MISMATCH', 403);
    }

    const materialized = await client.query(
      `SELECT 1
         FROM lecture_instances li
         JOIN timetable_entries te ON te.id = li.timetable_entry_id
        WHERE te.timetable_upload_id = $1
        LIMIT 1`,
      [id]
    );

    if ((materialized.rowCount ?? 0) > 0) {
      throw makeError('TIMETABLE_LOCKED', 409);
    }

    await assertReferencedRowsExist(client, normalized.entries);
    await client.query(`UPDATE timetable_uploads SET status = 'UPDATED', source_filename = $2, checksum = $3 WHERE id = $1`, [id, normalized.sourceFilename, normalized.checksum]);

    const currentEntries = await loadEntries(client, id);
    const currentEntryIds = new Set(currentEntries.map((row) => row.id));
    const desiredEntryIds = new Set(normalized.entries.map((entry) => entry.entryId));

    for (const current of currentEntries) {
      if (!desiredEntryIds.has(current.id)) {
        await client.query(`DELETE FROM timetable_entries WHERE id = $1`, [current.id]);
      }
    }

    for (const entry of normalized.entries) {
      if (currentEntryIds.has(entry.entryId)) {
        await client.query(
          `UPDATE timetable_entries
              SET day_of_week = $2,
                  classroom_id = $3::uuid,
                  teacher_id = $4::uuid,
                  course_name = $5,
                  start_time_local = $6::time,
                  end_time_local = $7::time,
                  lecture_duration_minutes = $8,
                  join_window_minutes = $9,
                  active_flag = $10
            WHERE id = $1`,
          [entry.entryId, entry.dayOfWeek, entry.classroomId, entry.teacherId, entry.courseName, entry.startTime, entry.endTime, entry.lectureDurationMinutes, entry.joinWindowMinutes, entry.activeFlag]
        );
      } else {
        await client.query(
          `INSERT INTO timetable_entries (
             id, timetable_upload_id, day_of_week, classroom_id, teacher_id, course_name,
             start_time_local, end_time_local, lecture_duration_minutes, join_window_minutes, active_flag
           ) VALUES ($1::uuid, $2, $3, $4::uuid, $5::uuid, $6, $7::time, $8::time, $9, $10, $11)`,
          [entry.entryId, id, entry.dayOfWeek, entry.classroomId, entry.teacherId, entry.courseName, entry.startTime, entry.endTime, entry.lectureDurationMinutes, entry.joinWindowMinutes, entry.activeFlag]
        );
      }
    }

    return { timetableId: id, status: 'UPDATED', entryCount: normalized.entries.length };
  });
}

export async function deleteTimetable(timetableId: string, actorId: string, isAdmin = false) {
  const userId = assertActor(actorId);
  const id = assertActor(timetableId);

  return runInTransaction(async (client) => {
    const upload = await loadUpload(client, id);
    if (!upload || upload.status === 'DELETED') {
      throw makeError('TIMETABLE_NOT_FOUND', 404);
    }

    if (!isAdmin && upload.uploaded_by !== userId) {
      throw makeError('TIMETABLE_OWNERSHIP_MISMATCH', 403);
    }

    const materialized = await client.query(
      `SELECT 1
         FROM lecture_instances li
         JOIN timetable_entries te ON te.id = li.timetable_entry_id
        WHERE te.timetable_upload_id = $1
        LIMIT 1`,
      [id]
    );

    if ((materialized.rowCount ?? 0) > 0) {
      throw makeError('TIMETABLE_LOCKED', 409);
    }

    await client.query(`UPDATE timetable_uploads SET status = 'DELETED' WHERE id = $1`, [id]);
    await client.query(`UPDATE timetable_entries SET active_flag = FALSE WHERE timetable_upload_id = $1`, [id]);

    return { deleted: true, timetableId: id };
  });
}

export function getTimetableErrorStatus(error: unknown) {
  return (error as { statusCode?: number })?.statusCode || 500;
}

export function getTimetableErrorMessage(error: unknown) {
  return (error as { message?: string })?.message || 'internal_error';
}