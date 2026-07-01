import { pool } from '../config/db';
import type { PoolClient } from 'pg';
import { getCurrentAcademicDate, listLectureInstances } from './lectureMaterializationService';
import {
  createDefaultTeacherPresenceProvider,
  TeacherPresenceProvider,
  type TeacherPresenceResult
} from './teacherPresenceProvider';
import { triggerTeacherReferenceCapture } from './teacherReferenceCapture';

type LectureActivationRow = {
  lectureInstanceId: string;
  timetableEntryId: string;
  lectureDate: string;
  scheduledStartAt: string;
  scheduledEndAt: string;
  activationState: 'PENDING' | 'ACTIVE' | 'ENDED' | 'MISSED';
  activationReason: string | null;
  sessionId: string | null;
  sessionStatus: string | null;
  teacherId: string;
  classroomId: string;
  courseName: string;
  joinWindowMinutes: number;
};

type LectureActivationSummary = {
  academicDate: string;
  evaluatedAt: string;
  totalLectureInstances: number;
  pendingCount: number;
  activeCount: number;
  endedCount: number;
  missedCount: number;
  lectureInstances: LectureActivationRow[];
};

type EngineResult = LectureActivationSummary & {
  activatedCount: number;
  endedLectureCount: number;
  missedLectureCount: number;
};

type LectureActivationDependencies = {
  teacherPresenceProvider?: TeacherPresenceProvider;
  now?: Date;
  triggerTeacherReferenceCapture?: typeof triggerTeacherReferenceCapture;
};

const ACTIVATION_REASON = 'TIMETABLE_AND_TEACHER_PRESENCE_MATCH';
const END_REASON = 'SCHEDULED_END_REACHED';
const MISSED_REASON = 'LECTURE_WINDOW_EXPIRED_WITHOUT_ACTIVATION';

const LECTURE_ROW_QUERY = `
  SELECT li.id AS lectureInstanceId,
         li.timetable_entry_id AS timetableEntryId,
         li.lecture_date::text AS lectureDate,
         li.scheduled_start_at::text AS scheduledStartAt,
         li.scheduled_end_at::text AS scheduledEndAt,
         li.activation_state AS activationState,
         li.activation_reason AS activationReason,
         li.session_id AS sessionId,
         s.status AS sessionStatus,
         te.teacher_id AS teacherId,
         te.classroom_id AS classroomId,
         te.course_name AS courseName,
         te.join_window_minutes AS joinWindowMinutes
    FROM lecture_instances li
    INNER JOIN timetable_entries te ON te.id = li.timetable_entry_id
    LEFT JOIN sessions s ON s.id = li.session_id
   WHERE li.lecture_date = $1::date
   ORDER BY li.scheduled_start_at, li.id
`;

const LECTURE_ROW_QUERY_FOR_UPDATE = `${LECTURE_ROW_QUERY}
FOR UPDATE OF li`;

function makeError(message: string, statusCode: number) {
  const error = new Error(message) as Error & { statusCode?: number };
  error.statusCode = statusCode;
  return error;
}

function normalizeNow(now = new Date()) {
  return now.toISOString();
}

function isOnOrAfter(value: string, compareTo: string) {
  return new Date(value).getTime() >= new Date(compareTo).getTime();
}

function isBefore(value: string, compareTo: string) {
  return new Date(value).getTime() < new Date(compareTo).getTime();
}

function toActivationRow(row: any): LectureActivationRow {
  return {
    lectureInstanceId: String(row.lectureinstanceid ?? row.lectureInstanceId),
    timetableEntryId: String(row.timetableentryid ?? row.timetableEntryId),
    lectureDate: String(row.lecturedate ?? row.lectureDate),
    scheduledStartAt: String(row.scheduledstartat ?? row.scheduledStartAt),
    scheduledEndAt: String(row.scheduledendat ?? row.scheduledEndAt),
    activationState: String(row.activationstate ?? row.activationState) as LectureActivationRow['activationState'],
    activationReason: row.activationreason ?? row.activationReason ?? null,
    sessionId: row.sessionid ?? row.sessionId ?? null,
    sessionStatus: row.sessionstatus ?? row.sessionStatus ?? null,
    teacherId: String(row.teacherid ?? row.teacherId),
    classroomId: String(row.classroomid ?? row.classroomId),
    courseName: String(row.coursename ?? row.courseName),
    joinWindowMinutes: Number(row.joinwindowminutes ?? row.joinWindowMinutes ?? 0)
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
      // ignore rollback failure and rethrow the original error
    }
    throw error;
  } finally {
    client.release();
  }
}

async function loadRows(client: PoolClient, academicDate: string, lockRows: boolean) {
  const result = await client.query(lockRows ? LECTURE_ROW_QUERY_FOR_UPDATE : LECTURE_ROW_QUERY, [academicDate]);
  return (result.rows as any[]).map(toActivationRow);
}

async function insertSessionForLecture(client: PoolClient, row: LectureActivationRow, activatedAt: string) {
  const result = await client.query(
    `INSERT INTO sessions (
       classroom_id,
       teacher_id,
       course_name,
       status,
       start_time,
       join_window_minutes
     ) VALUES ($1, $2, $3, 'ACTIVE', $4::timestamptz, $5)
     RETURNING id`,
    [row.classroomId, row.teacherId, row.courseName, activatedAt, row.joinWindowMinutes]
  );

  return String(result.rows[0].id);
}

async function markLectureActive(client: PoolClient, row: LectureActivationRow, activatedAt: string, sessionId: string) {
  const result = await client.query(
    `UPDATE lecture_instances
        SET activation_state = 'ACTIVE',
            activation_reason = $2,
            session_id = $3
      WHERE id = $1
        AND activation_state = 'PENDING'
        AND (session_id IS NULL OR session_id = $3)
      RETURNING id`,
    [row.lectureInstanceId, ACTIVATION_REASON, sessionId]
  );

  if ((result.rowCount ?? 0) === 0) {
    return false;
  }

  await client.query(
    `UPDATE sessions
        SET status = 'ACTIVE',
            start_time = COALESCE(start_time, $2::timestamptz)
      WHERE id = $1`,
    [sessionId, activatedAt]
  );

  return true;
}

async function markLectureEnded(client: PoolClient, row: LectureActivationRow, endedAt: string) {
  if (row.sessionId) {
    await client.query(
      `UPDATE sessions
          SET status = 'CLOSED',
              end_time = COALESCE(end_time, $2::timestamptz)
        WHERE id = $1`,
      [row.sessionId, endedAt]
    );
  }

  await client.query(
    `UPDATE lecture_instances
        SET activation_state = 'ENDED',
            activation_reason = $2
      WHERE id = $1
        AND activation_state = 'ACTIVE'`,
    [row.lectureInstanceId, END_REASON]
  );
}

async function markLectureMissed(client: PoolClient, row: LectureActivationRow) {
  await client.query(
    `UPDATE lecture_instances
        SET activation_state = 'MISSED',
            activation_reason = $2
      WHERE id = $1
        AND activation_state = 'PENDING'`,
    [row.lectureInstanceId, MISSED_REASON]
  );
}

function summarizeRows(academicDate: string, rows: LectureActivationRow[], evaluatedAt: string): LectureActivationSummary {
  return {
    academicDate,
    evaluatedAt,
    totalLectureInstances: rows.length,
    pendingCount: rows.filter((row) => row.activationState === 'PENDING').length,
    activeCount: rows.filter((row) => row.activationState === 'ACTIVE').length,
    endedCount: rows.filter((row) => row.activationState === 'ENDED').length,
    missedCount: rows.filter((row) => row.activationState === 'MISSED').length,
    lectureInstances: rows
  };
}

function createEngine(dependencies: LectureActivationDependencies = {}) {
  const teacherPresenceProvider = dependencies.teacherPresenceProvider ?? createDefaultTeacherPresenceProvider();
  const captureTrigger = dependencies.triggerTeacherReferenceCapture ?? triggerTeacherReferenceCapture;

  function resolveNow() {
    return dependencies.now ?? new Date();
  }

  async function getRowsForToday(lockRows: boolean) {
    const academicDate = getCurrentAcademicDate(resolveNow());
    const client = lockRows ? await pool.connect() : null;

    if (!client) {
      const result = await pool.query(LECTURE_ROW_QUERY, [academicDate]);
      return {
        academicDate,
        rows: (result.rows as any[]).map(toActivationRow)
      };
    }

    try {
      await client.query('BEGIN');
      const rows = await loadRows(client, academicDate, true);
      await client.query('COMMIT');
      return { academicDate, rows };
    } catch (error) {
      try {
        await client.query('ROLLBACK');
      } catch (_rollbackError) {
        // ignore rollback errors
      }
      throw error;
    } finally {
      client.release();
    }
  }

  async function getTodayLectureInstances() {
    const academicDate = getCurrentAcademicDate(resolveNow());
    const result = await listLectureInstances({ academicDate });
    return {
      academicDate,
      lectureInstances: result.lectureInstances
    };
  }

  async function evaluateTodayLectures() {
    const academicDate = getCurrentAcademicDate(resolveNow());
    const result = await pool.query(LECTURE_ROW_QUERY, [academicDate]);
    const rows = (result.rows as any[]).map(toActivationRow);
    return summarizeRows(academicDate, rows, normalizeNow(resolveNow()));
  }

  async function verifyTeacherPresence(teacherId: string, classroomId: string, lectureInstanceId: string, timestamp = resolveNow()): Promise<TeacherPresenceResult> {
    const normalizedTeacherId = teacherId.trim();
    const normalizedClassroomId = classroomId.trim();
    const normalizedLectureInstanceId = lectureInstanceId.trim();

    if (!normalizedTeacherId || !normalizedClassroomId || !normalizedLectureInstanceId) {
      throw makeError('MISSING_REQUIRED_FIELDS', 400);
    }

    return teacherPresenceProvider.isTeacherPresent(
      normalizedTeacherId,
      normalizedClassroomId,
      normalizedLectureInstanceId,
      timestamp
    );
  }

  async function activateRows(client: PoolClient, rows: LectureActivationRow[], evaluatedAt: string) {
    let activatedCount = 0;
    let missedLectureCount = 0;

    for (const row of rows) {
      if (row.activationState !== 'PENDING') {
        continue;
      }

      if (isBefore(evaluatedAt, row.scheduledStartAt)) {
        continue;
      }

      if (isOnOrAfter(evaluatedAt, row.scheduledEndAt)) {
        await markLectureMissed(client, row);
        row.activationState = 'MISSED';
        row.activationReason = MISSED_REASON;
        missedLectureCount += 1;
        continue;
      }

      const teacherPresence = await teacherPresenceProvider.isTeacherPresent(
        row.teacherId,
        row.classroomId,
        row.lectureInstanceId,
        new Date(evaluatedAt)
      );

      if (!teacherPresence.present) {
        continue;
      }

      const sessionId = row.sessionId ?? await insertSessionForLecture(client, row, evaluatedAt);
      const activated = await markLectureActive(client, row, evaluatedAt, sessionId);

      if (!activated) {
        continue;
      }

      row.sessionId = sessionId;
      row.activationState = 'ACTIVE';
      row.activationReason = ACTIVATION_REASON;
      row.sessionStatus = 'ACTIVE';
      activatedCount += 1;

      await captureTrigger({
        teacherId: row.teacherId,
        classroomId: row.classroomId,
        lectureInstanceId: row.lectureInstanceId,
        sessionId,
        timestamp: evaluatedAt
      });
    }

    return { activatedCount, missedLectureCount };
  }

  async function closeRows(client: PoolClient, rows: LectureActivationRow[], evaluatedAt: string) {
    let endedLectureCount = 0;
    let missedLectureCount = 0;

    for (const row of rows) {
      if (isBefore(evaluatedAt, row.scheduledEndAt)) {
        continue;
      }

      if (row.activationState === 'ACTIVE') {
        await markLectureEnded(client, row, evaluatedAt);
        row.activationState = 'ENDED';
        row.activationReason = END_REASON;
        row.sessionStatus = 'CLOSED';
        endedLectureCount += 1;
        continue;
      }

      if (row.activationState === 'PENDING') {
        await markLectureMissed(client, row);
        row.activationState = 'MISSED';
        row.activationReason = MISSED_REASON;
        missedLectureCount += 1;
      }
    }

    return { endedLectureCount, missedLectureCount };
  }

  async function run() {
    const academicDate = getCurrentAcademicDate(resolveNow());
    const evaluatedAt = normalizeNow(resolveNow());

    return runInTransaction(async (client) => {
      const rows = await loadRows(client, academicDate, true);
      const activationResult = await activateRows(client, rows, evaluatedAt);
      const closureResult = await closeRows(client, rows, evaluatedAt);

      return {
        ...summarizeRows(academicDate, rows, evaluatedAt),
        activatedCount: activationResult.activatedCount,
        endedLectureCount: closureResult.endedLectureCount,
        missedLectureCount: activationResult.missedLectureCount + closureResult.missedLectureCount
      } satisfies EngineResult;
    });
  }

  async function activateEligibleLectures() {
    const academicDate = getCurrentAcademicDate(resolveNow());
    const evaluatedAt = normalizeNow(resolveNow());

    return runInTransaction(async (client) => {
      const rows = await loadRows(client, academicDate, true);
      const activationResult = await activateRows(client, rows, evaluatedAt);

      return {
        ...summarizeRows(academicDate, rows, evaluatedAt),
        activatedCount: activationResult.activatedCount,
        endedLectureCount: 0,
        missedLectureCount: activationResult.missedLectureCount
      } satisfies EngineResult;
    });
  }

  async function closeCompletedLectures() {
    const academicDate = getCurrentAcademicDate(resolveNow());
    const evaluatedAt = normalizeNow(resolveNow());

    return runInTransaction(async (client) => {
      const rows = await loadRows(client, academicDate, true);
      const closureResult = await closeRows(client, rows, evaluatedAt);

      return {
        ...summarizeRows(academicDate, rows, evaluatedAt),
        activatedCount: 0,
        endedLectureCount: closureResult.endedLectureCount,
        missedLectureCount: closureResult.missedLectureCount
      } satisfies EngineResult;
    });
  }

  return {
    run,
    status: evaluateTodayLectures,
    activateEligibleLectures,
    closeCompletedLectures,
    verifyTeacherPresence,
    getTodayLectureInstances
  };
}

export function createLectureActivationEngine(dependencies: LectureActivationDependencies = {}) {
  return createEngine(dependencies);
}

const defaultEngine = createEngine();

export async function runActivationEngine() {
  return defaultEngine.run();
}

export async function evaluateTodayLectures() {
  return defaultEngine.status();
}

export async function activateEligibleLectures() {
  return defaultEngine.activateEligibleLectures();
}

export async function closeCompletedLectures() {
  return defaultEngine.closeCompletedLectures();
}

export async function verifyTeacherPresence(
  teacherId: string,
  classroomId: string,
  lectureInstanceId: string,
  timestamp = new Date(),
  provider = createDefaultTeacherPresenceProvider()
) {
  if (!teacherId.trim() || !classroomId.trim() || !lectureInstanceId.trim()) {
    throw makeError('MISSING_REQUIRED_FIELDS', 400);
  }

  return provider.isTeacherPresent(teacherId.trim(), classroomId.trim(), lectureInstanceId.trim(), timestamp);
}

export async function getTodayLectureInstances() {
  return defaultEngine.getTodayLectureInstances();
}

export function getLectureActivationErrorStatus(error: unknown) {
  return (error as { statusCode?: number })?.statusCode || 500;
}

export function getLectureActivationErrorMessage(error: unknown) {
  return (error as Error)?.message || 'internal_error';
}
