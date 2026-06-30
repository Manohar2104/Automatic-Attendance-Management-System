import { pool } from '../config/db';
import type { PoolClient } from 'pg';

const DEFAULT_ACADEMIC_TIME_ZONE = process.env.ACADEMIC_TIME_ZONE || 'Asia/Kolkata';

export const LectureLifecycleState = {
  PENDING: 'PENDING',
  ACTIVE: 'ACTIVE',
  ENDED: 'ENDED',
  CANCELLED: 'CANCELLED',
  MISSED: 'MISSED'
} as const;

type MaterializeResultRow = {
  id: string;
  timetable_entry_id: string;
  session_id: string | null;
  lecture_date: string;
  scheduled_start_at: string;
  scheduled_end_at: string;
  activation_state: string;
  activation_reason: string | null;
  created_at: string | null;
};

type TimetableUploadRow = {
  id: string;
  uploaded_by: string;
  academic_week_start: string;
  status: string;
};

type TimetableEntryRow = {
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

type LectureInstanceRecord = {
  lectureInstanceId: string;
  timetableEntryId: string;
  sessionId: string | null;
  lectureDate: string;
  scheduledStartAt: string;
  scheduledEndAt: string;
  activationState: string;
  activationReason: string | null;
};

type MaterializationResult = {
  lectureInstances: LectureInstanceRecord[];
  generatedCount: number;
  existingCount: number;
  academicDate: string;
  materializationSource: 'MANUAL_TRIGGER' | 'BACKFILL' | 'RECOVERY' | 'SCHEDULER';
};

function makeError(message: string, statusCode: number) {
  const error = new Error(message) as Error & { statusCode?: number };
  error.statusCode = statusCode;
  return error;
}

function trim(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizeAcademicDate(value: unknown) {
  const academicDate = trim(value);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(academicDate)) {
    throw makeError('INVALID_ACADEMIC_DATE', 400);
  }

  return academicDate;
}

function formatAcademicDate(now = new Date(), timeZone = DEFAULT_ACADEMIC_TIME_ZONE) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(now);

  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  if (!values.year || !values.month || !values.day) {
    throw makeError('INVALID_ACADEMIC_DATE', 400);
  }

  return `${values.year}-${values.month}-${values.day}`;
}

function academicWeekStartForDate(academicDate: string) {
  const parsed = new Date(`${academicDate}T00:00:00.000Z`);
  if (Number.isNaN(parsed.getTime())) {
    throw makeError('INVALID_ACADEMIC_DATE', 400);
  }

  const day = parsed.getUTCDay();
  const mondayOffset = day === 0 ? -6 : 1 - day;
  const weekStart = new Date(parsed);
  weekStart.setUTCDate(parsed.getUTCDate() + mondayOffset);
  return weekStart.toISOString().slice(0, 10);
}

function dayNameForAcademicDate(academicDate: string) {
  const parsed = new Date(`${academicDate}T00:00:00.000Z`);
  if (Number.isNaN(parsed.getTime())) {
    throw makeError('INVALID_ACADEMIC_DATE', 400);
  }

  return new Intl.DateTimeFormat('en-US', {
    timeZone: DEFAULT_ACADEMIC_TIME_ZONE,
    weekday: 'long'
  }).format(parsed).toUpperCase();
}

function parseLocalTime(value: string) {
  const trimmed = trim(value);
  const match = /^(\d{2}):(\d{2})(?::\d{2}(?:\.\d{1,3})?)?$/.exec(trimmed);
  if (!match) {
    throw makeError('INVALID_TIME_RANGE', 400);
  }

  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
    throw makeError('INVALID_TIME_RANGE', 400);
  }

  return { hours, minutes };
}

function fixedOffsetMinutes(timeZone: string) {
  if (timeZone === 'UTC') {
    return 0;
  }

  if (timeZone === 'Asia/Kolkata') {
    return 330;
  }

  throw makeError('UNSUPPORTED_ACADEMIC_TIME_ZONE', 422);
}

function localDateTimeToUtc(academicDate: string, localTime: string, timeZone = DEFAULT_ACADEMIC_TIME_ZONE) {
  const { hours, minutes } = parseLocalTime(localTime);
  const [year, month, day] = academicDate.split('-').map(Number);
  const offsetMinutes = fixedOffsetMinutes(timeZone);
  const utcMillis = Date.UTC(year, month - 1, day, hours, minutes) - (offsetMinutes * 60 * 1000);
  return new Date(utcMillis).toISOString();
}

function rowToRecord(row: MaterializeResultRow): LectureInstanceRecord {
  return {
    lectureInstanceId: row.id,
    timetableEntryId: row.timetable_entry_id,
    sessionId: row.session_id,
    lectureDate: row.lecture_date,
    scheduledStartAt: row.scheduled_start_at,
    scheduledEndAt: row.scheduled_end_at,
    activationState: row.activation_state,
    activationReason: row.activation_reason
  };
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
      // Preserve original failure.
    }
    throw error;
  } finally {
    client.release();
  }
}

async function loadActiveUploadForDate(client: PoolClient, academicDate: string) {
  const weekStart = academicWeekStartForDate(academicDate);
  const result = await client.query<TimetableUploadRow>(
    `SELECT id, uploaded_by, academic_week_start::text AS academic_week_start, status
       FROM timetable_uploads
      WHERE status <> 'DELETED'
        AND academic_week_start = $1::date
      ORDER BY uploaded_at DESC
      LIMIT 1`,
    [weekStart]
  );

  return result.rowCount ? result.rows[0] : null;
}

async function loadEntriesForUploadAndDay(client: PoolClient, uploadId: string, dayOfWeek: string) {
  const result = await client.query<TimetableEntryRow>(
    `SELECT id, timetable_upload_id, day_of_week, classroom_id, teacher_id, course_name, start_time_local, end_time_local, lecture_duration_minutes, join_window_minutes, active_flag, created_at::text AS created_at
       FROM timetable_entries
      WHERE timetable_upload_id = $1
        AND active_flag = TRUE
        AND day_of_week = $2
      ORDER BY start_time_local, id`,
    [uploadId, dayOfWeek]
  );

  return result.rows;
}

async function loadEntriesByIds(client: PoolClient, academicDate: string, timetableEntryIds: string[]) {
  const result = await client.query<TimetableEntryRow>(
    `SELECT te.id, te.timetable_upload_id, te.day_of_week, te.classroom_id, te.teacher_id, te.course_name, te.start_time_local, te.end_time_local, te.lecture_duration_minutes, te.join_window_minutes, te.active_flag, te.created_at::text AS created_at
       FROM timetable_entries te
       JOIN timetable_uploads tu ON tu.id = te.timetable_upload_id
      WHERE te.id = ANY($1::uuid[])
        AND te.active_flag = TRUE
        AND tu.status <> 'DELETED'
        AND tu.academic_week_start = $2::date
      ORDER BY te.day_of_week, te.start_time_local, te.id`,
    [timetableEntryIds, academicWeekStartForDate(academicDate)]
  );

  return result.rows;
}

async function insertLectureInstances(client: PoolClient, academicDate: string, entries: TimetableEntryRow[]) {
  if (entries.length === 0) {
    throw makeError('EMPTY_TIMETABLE', 404);
  }

  const createdRows: MaterializeResultRow[] = [];
  for (const entry of entries) {
    const scheduledStartAt = localDateTimeToUtc(academicDate, entry.start_time_local);
    const scheduledEndAt = localDateTimeToUtc(academicDate, entry.end_time_local);
    const result = await client.query<MaterializeResultRow>(
      `INSERT INTO lecture_instances (
         timetable_entry_id,
         lecture_date,
         scheduled_start_at,
         scheduled_end_at,
         activation_state,
         activation_reason
       ) VALUES ($1, $2::date, $3::timestamptz, $4::timestamptz, 'PENDING', NULL)
       ON CONFLICT (timetable_entry_id, lecture_date)
       DO NOTHING
       RETURNING id, timetable_entry_id, session_id, lecture_date::text AS lecture_date, scheduled_start_at::text AS scheduled_start_at, scheduled_end_at::text AS scheduled_end_at, activation_state, activation_reason, created_at::text AS created_at`,
      [entry.id, academicDate, scheduledStartAt, scheduledEndAt]
    );

    if ((result.rowCount ?? 0) > 0) {
      createdRows.push(result.rows[0]);
    }
  }

  return createdRows;
}

async function loadLectureInstances(client: PoolClient, academicDate: string, entryIds: string[]) {
  if (entryIds.length === 0) {
    return [];
  }

  const result = await client.query<MaterializeResultRow>(
    `SELECT id, timetable_entry_id, session_id, lecture_date::text AS lecture_date, scheduled_start_at::text AS scheduled_start_at, scheduled_end_at::text AS scheduled_end_at, activation_state, activation_reason, created_at::text AS created_at
       FROM lecture_instances
      WHERE lecture_date = $1::date
        AND timetable_entry_id = ANY($2::uuid[])
      ORDER BY scheduled_start_at, id`,
    [academicDate, entryIds]
  );

  return result.rows;
}

export function getCurrentAcademicDate(now = new Date()) {
  return formatAcademicDate(now);
}

export async function materializeDay(academicDateInput: string, materializationSource: MaterializationResult['materializationSource'] = 'MANUAL_TRIGGER') {
  const academicDate = normalizeAcademicDate(academicDateInput);

  return runInTransaction(async (client) => {
    const upload = await loadActiveUploadForDate(client, academicDate);
    if (!upload) {
      throw makeError('TIMETABLE_NOT_FOUND', 404);
    }

    const dayOfWeek = dayNameForAcademicDate(academicDate);
    const entries = await loadEntriesForUploadAndDay(client, upload.id, dayOfWeek);
    if (entries.length === 0) {
      throw makeError('EMPTY_TIMETABLE', 404);
    }

    const createdRows = await insertLectureInstances(client, academicDate, entries);
    const lectureInstances = await loadLectureInstances(client, academicDate, entries.map((entry) => entry.id));

    return {
      academicDate,
      generatedCount: createdRows.length,
      existingCount: lectureInstances.length - createdRows.length,
      lectureInstances: lectureInstances.map(rowToRecord),
      materializationSource
    } satisfies MaterializationResult;
  });
}

export async function materializeRange(academicDateInput: string, timetableEntryIds: string[], materializationSource: MaterializationResult['materializationSource'] = 'MANUAL_TRIGGER') {
  const academicDate = normalizeAcademicDate(academicDateInput);
  const entryIds = Array.from(new Set((Array.isArray(timetableEntryIds) ? timetableEntryIds : []).map(trim).filter(Boolean)));
  if (entryIds.length === 0) {
    throw makeError('EMPTY_TIMETABLE', 404);
  }

  return runInTransaction(async (client) => {
    const entries = await loadEntriesByIds(client, academicDate, entryIds);
    if (entries.length === 0) {
      throw makeError('TIMETABLE_NOT_FOUND', 404);
    }

    if (entries.length !== entryIds.length) {
      throw makeError('INVALID_TIMETABLE', 404);
    }

    const createdRows = await insertLectureInstances(client, academicDate, entries);
    const lectureInstances = await loadLectureInstances(client, academicDate, entryIds);

    return {
      academicDate,
      generatedCount: createdRows.length,
      existingCount: lectureInstances.length - createdRows.length,
      lectureInstances: lectureInstances.map(rowToRecord),
      materializationSource
    } satisfies MaterializationResult;
  });
}

export async function materializeWeek(academicWeekStartInput: string, materializationSource: MaterializationResult['materializationSource'] = 'MANUAL_TRIGGER') {
  const academicWeekStart = normalizeAcademicDate(academicWeekStartInput);
  const start = new Date(`${academicWeekStart}T00:00:00.000Z`);
  const dates: string[] = [];
  for (let index = 0; index < 7; index += 1) {
    const current = new Date(start);
    current.setUTCDate(start.getUTCDate() + index);
    dates.push(current.toISOString().slice(0, 10));
  }

  const results: MaterializationResult[] = [];
  for (const date of dates) {
    try {
      results.push(await materializeDay(date, materializationSource));
    } catch (error) {
      if ((error as any)?.statusCode === 404 && (error as Error).message === 'EMPTY_TIMETABLE') {
        continue;
      }

      throw error;
    }
  }

  return { academicWeekStart, runs: results };
}

export async function materializeLectureInstancesForAcademicDate(academicDateInput: string) {
  return materializeDay(academicDateInput);
}

export async function materializeLectureInstancesForEntryIds(academicDateInput: string, timetableEntryIds: string[]) {
  return materializeRange(academicDateInput, timetableEntryIds);
}

export async function materializeAcademicWeek(academicWeekStartInput: string) {
  return materializeWeek(academicWeekStartInput);
}

export async function listLectureInstances(filters: { academicDate?: string; timetableEntryId?: string } = {}) {
  const conditions: string[] = ['1 = 1'];
  const values: Array<string> = [];

  if (filters.academicDate) {
    values.push(normalizeAcademicDate(filters.academicDate));
    conditions.push(`li.lecture_date = $${values.length}::date`);
  }

  if (filters.timetableEntryId) {
    values.push(trim(filters.timetableEntryId));
    conditions.push(`li.timetable_entry_id = $${values.length}::uuid`);
  }

  const result = await pool.query<MaterializeResultRow>(
    `SELECT id, timetable_entry_id, session_id, lecture_date::text AS lecture_date, scheduled_start_at::text AS scheduled_start_at, scheduled_end_at::text AS scheduled_end_at, activation_state, activation_reason, created_at::text AS created_at
       FROM lecture_instances li
      WHERE ${conditions.join(' AND ')}
      ORDER BY lecture_date, scheduled_start_at, id`,
    values
  );

  return { lectureInstances: result.rows.map(rowToRecord) };
}

export async function getLectureInstance(lectureInstanceId: string) {
  const trimmedId = trim(lectureInstanceId);
  if (!trimmedId) {
    throw makeError('MISSING_LECTURE_INSTANCE_ID', 400);
  }

  const result = await pool.query<MaterializeResultRow>(
    `SELECT id, timetable_entry_id, session_id, lecture_date::text AS lecture_date, scheduled_start_at::text AS scheduled_start_at, scheduled_end_at::text AS scheduled_end_at, activation_state, activation_reason, created_at::text AS created_at
       FROM lecture_instances
      WHERE id = $1
      LIMIT 1`,
    [trimmedId]
  );

  return result.rowCount ? rowToRecord(result.rows[0]) : null;
}

export function getLectureMaterializationErrorStatus(error: unknown) {
  return (error as { statusCode?: number })?.statusCode || 500;
}

export function getLectureMaterializationErrorMessage(error: unknown) {
  return (error as Error)?.message || 'internal_error';
}