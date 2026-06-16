import { pool } from '../config/db';
import type { PoolClient } from 'pg';

export type SessionThresholdInput = {
  presenceThresholdPresent?: number | string | null;
  presenceThresholdPartial?: number | string | null;
};

export type SessionCreateInput = SessionThresholdInput & {
  teacherId: string;
  classroomId: string;
  courseName: string;
};

export type SessionJoinInput = {
  sessionId: string;
  studentId: string;
  joinedAt?: Date;
};

export type JoinOutcome = {
  joinScore: number;
  attendanceStatus: 'PRESENT' | 'PARTIAL' | 'REJECTED';
  joinTime: string;
};

export type SessionRecord = {
  id: string;
  classroom_id: string | null;
  teacher_id: string | null;
  course_name: string | null;
  status: string;
  start_time: string | null;
  end_time: string | null;
  presence_threshold_present: number | null;
  presence_threshold_partial: number | null;
  join_window_minutes: number | null;
  created_at: string | null;
};

export const DEFAULT_PRESENCE_THRESHOLD_PRESENT = 85;
export const DEFAULT_PRESENCE_THRESHOLD_PARTIAL = 60;

export function parseOptionalInteger(value: number | string | null | undefined) {
  if (value === null || value === undefined || value === '') return null;
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? Math.trunc(parsed) : NaN;
}

export function validatePresenceThresholds(input: SessionThresholdInput) {
  const presenceThresholdPresent = parseOptionalInteger(input.presenceThresholdPresent) ?? DEFAULT_PRESENCE_THRESHOLD_PRESENT;
  const presenceThresholdPartial = parseOptionalInteger(input.presenceThresholdPartial) ?? DEFAULT_PRESENCE_THRESHOLD_PARTIAL;

  if (
    !Number.isInteger(presenceThresholdPresent) ||
    !Number.isInteger(presenceThresholdPartial) ||
    presenceThresholdPresent < 70 ||
    presenceThresholdPresent > 100 ||
    presenceThresholdPartial < 40 ||
    presenceThresholdPartial > 84 ||
    presenceThresholdPartial >= presenceThresholdPresent
  ) {
    const error = new Error('THRESHOLD_CONFLICT');
    (error as any).statusCode = 400;
    throw error;
  }

  return { presenceThresholdPresent, presenceThresholdPartial };
}

export function computeJoinOutcome(startTime: Date, joinedAt: Date = new Date()): JoinOutcome {
  const elapsedMs = joinedAt.getTime() - startTime.getTime();
  const elapsedMinutes = elapsedMs / 60000;

  if (elapsedMinutes <= 2) {
    return { joinScore: 100, attendanceStatus: 'PRESENT', joinTime: joinedAt.toISOString() };
  }

  if (elapsedMinutes <= 5) {
    return { joinScore: 50, attendanceStatus: 'PARTIAL', joinTime: joinedAt.toISOString() };
  }

  return { joinScore: 0, attendanceStatus: 'REJECTED', joinTime: joinedAt.toISOString() };
}

/**
 * Internal helper: checks if a heartbeat should be rejected based on attendance status.
 * Used by future Heartbeat_Processor (Phase 6+) to enforce Requirement 4 AC #9.
 * NOT exposed as a public route.
 * @internal
 */
export function shouldRejectHeartbeatForAttendanceStatus(attendanceStatus?: string | null) {
  return attendanceStatus === 'REJECTED';
}

function sessionRowToApi(row: SessionRecord) {
  return {
    sessionId: row.id,
    classroomId: row.classroom_id,
    teacherId: row.teacher_id,
    courseName: row.course_name,
    status: row.status,
    startTime: row.start_time,
    endTime: row.end_time,
    presenceThresholdPresent: row.presence_threshold_present,
    presenceThresholdPartial: row.presence_threshold_partial,
    joinWindowMinutes: row.join_window_minutes,
    createdAt: row.created_at
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
      // Ignore rollback errors to preserve the original failure signal.
    }
    throw error;
  } finally {
    client.release();
  }
}

export async function createSession(input: SessionCreateInput) {
  const teacherId = input.teacherId?.trim();
  const classroomId = input.classroomId?.trim();
  const courseName = input.courseName?.trim();

  if (!teacherId || !classroomId || !courseName) {
    const error = new Error('MISSING_REQUIRED_FIELDS');
    (error as any).statusCode = 400;
    throw error;
  }

  const thresholds = validatePresenceThresholds(input);

  return runInTransaction(async (client) => {
    const activeCheck = await client.query(
      `SELECT id FROM sessions WHERE teacher_id = $1 AND classroom_id = $2 AND status = 'ACTIVE' LIMIT 1`,
      [teacherId, classroomId]
    );

    if ((activeCheck.rowCount ?? 0) > 0) {
      const error = new Error('ACTIVE_SESSION_EXISTS');
      (error as any).statusCode = 409;
      throw error;
    }

    const result = await client.query(
      `INSERT INTO sessions (classroom_id, teacher_id, course_name, status, start_time, presence_threshold_present, presence_threshold_partial)
       VALUES ($1, $2, $3, 'ACTIVE', now(), $4, $5)
       RETURNING *`,
      [classroomId, teacherId, courseName, thresholds.presenceThresholdPresent, thresholds.presenceThresholdPartial]
    );

    return sessionRowToApi(result.rows[0] as SessionRecord);
  });
}

// TODO(Requirement 4 AC #2): broadcast SESSION_STARTED via WebSocket in Phase 6.
export async function finalizeAttendanceForSession(sessionId: string) {
  return { sessionId, queued: true };
}

export async function endSession(sessionId: string) {
  const trimmedSessionId = sessionId?.trim();
  if (!trimmedSessionId) {
    const error = new Error('MISSING_SESSION_ID');
    (error as any).statusCode = 400;
    throw error;
  }

  return runInTransaction(async (client) => {
    const result = await client.query(`SELECT * FROM sessions WHERE id = $1 FOR UPDATE`, [trimmedSessionId]);
    if ((result.rowCount ?? 0) === 0 || result.rows[0].status !== 'ACTIVE') {
      const error = new Error('SESSION_NOT_ACTIVE');
      (error as any).statusCode = 404;
      throw error;
    }

    const updated = await client.query(
      `UPDATE sessions SET status = 'CLOSED', end_time = now() WHERE id = $1 RETURNING *`,
      [trimmedSessionId]
    );

    const finalization = await finalizeAttendanceForSession(trimmedSessionId);

    return {
      ...sessionRowToApi(updated.rows[0] as SessionRecord),
      finalization
    };
  });
}

export async function getSession(sessionId: string) {
  const trimmedSessionId = sessionId?.trim();
  if (!trimmedSessionId) {
    const error = new Error('MISSING_SESSION_ID');
    (error as any).statusCode = 400;
    throw error;
  }

  const result = await pool.query(`SELECT * FROM sessions WHERE id = $1 LIMIT 1`, [trimmedSessionId]);
  if ((result.rowCount ?? 0) === 0) {
    const error = new Error('SESSION_NOT_FOUND');
    (error as any).statusCode = 404;
    throw error;
  }

  return sessionRowToApi(result.rows[0] as SessionRecord);
}

export async function listActiveSessions() {
  const result = await pool.query(`SELECT * FROM sessions WHERE status = 'ACTIVE' ORDER BY start_time DESC`);
  return result.rows.map((row) => sessionRowToApi(row as SessionRecord));
}

export async function joinSession(input: SessionJoinInput) {
  const sessionId = input.sessionId?.trim();
  const studentId = input.studentId?.trim();
  if (!sessionId || !studentId) {
    const error = new Error('MISSING_REQUIRED_FIELDS');
    (error as any).statusCode = 400;
    throw error;
  }

  const joinedAt = input.joinedAt ?? new Date();

  return runInTransaction(async (client) => {
    const sessionResult = await client.query(`SELECT * FROM sessions WHERE id = $1 FOR UPDATE`, [sessionId]);
    if ((sessionResult.rowCount ?? 0) === 0 || sessionResult.rows[0].status !== 'ACTIVE') {
      const error = new Error('SESSION_NOT_FOUND_OR_ACTIVE');
      (error as any).statusCode = 404;
      throw error;
    }

    const attendanceResult = await client.query(
      `SELECT id FROM attendance WHERE session_id = $1 AND student_id = $2 LIMIT 1`,
      [sessionId, studentId]
    );

    if ((attendanceResult.rowCount ?? 0) > 0) {
      const error = new Error('STUDENT_ALREADY_ENROLLED');
      (error as any).statusCode = 409;
      throw error;
    }

    const sessionStart = new Date(sessionResult.rows[0].start_time);
    const outcome = computeJoinOutcome(sessionStart, joinedAt);

    await client.query(
      `INSERT INTO attendance (session_id, student_id, confidence_score, status, confidence_breakdown, join_time, join_score, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, now())`,
      [
        sessionId,
        studentId,
        0,
        outcome.attendanceStatus,
        JSON.stringify({ joinScore: outcome.joinScore, joinStatus: outcome.attendanceStatus, phase: 'phase5_join_tracking' }),
        outcome.joinTime,
        outcome.joinScore
      ]
    );

    return {
      sessionId,
      studentId,
      joinScore: outcome.joinScore,
      attendanceStatus: outcome.attendanceStatus,
      joinTime: outcome.joinTime
    };
  });
}
