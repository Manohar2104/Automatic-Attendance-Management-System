import { pool } from '../config/db';

type ConfidenceBreakdown = {
  acceptedHeartbeats: number;
  rejectedHeartbeats: number;
  lastHeartbeatSequence: number | null;
  lastHeartbeatStatus: string | null;
  lastHeartbeatTime: string | null;
};

export type StudentAttendanceHistoryRecord = {
  courseName: string | null;
  sessionId: string;
  status: string;
  joinScore: number;
  attendanceTimestamp: string;
  lastHeartbeatTimestamp: string | null;
  totalAcceptedHeartbeats: number;
  lastHeartbeatSequenceNumber: number | null;
  currentHeartbeatStatus: string | null;
};

export type StudentCurrentAttendanceRecord = StudentAttendanceHistoryRecord & {
  confidenceStatus: string;
};

function parseBreakdown(breakdown: unknown): ConfidenceBreakdown {
  if (!breakdown) {
    return {
      acceptedHeartbeats: 0,
      rejectedHeartbeats: 0,
      lastHeartbeatSequence: null,
      lastHeartbeatStatus: null,
      lastHeartbeatTime: null
    };
  }

  const record = typeof breakdown === 'string' ? JSON.parse(breakdown) : breakdown as Record<string, unknown>;

  return {
    acceptedHeartbeats: Number(record.acceptedHeartbeats ?? 0),
    rejectedHeartbeats: Number(record.rejectedHeartbeats ?? 0),
    lastHeartbeatSequence: record.lastHeartbeatSequence === null || record.lastHeartbeatSequence === undefined ? null : Number(record.lastHeartbeatSequence),
    lastHeartbeatStatus: typeof record.lastHeartbeatStatus === 'string' ? record.lastHeartbeatStatus : null,
    lastHeartbeatTime: typeof record.lastHeartbeatTime === 'string' ? record.lastHeartbeatTime : null
  };
}

function normalizeJoinScore(row: Record<string, unknown>) {
  const raw = row.join_score ?? row.join_score_value ?? 0;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : 0;
}

function rowToRecord(row: Record<string, unknown>): StudentAttendanceHistoryRecord {
  const breakdown = parseBreakdown(row.confidence_breakdown);
  return {
    courseName: typeof row.course_name === 'string' ? row.course_name : null,
    sessionId: String(row.session_id),
    status: String(row.status ?? 'UNKNOWN'),
    joinScore: normalizeJoinScore(row),
    attendanceTimestamp: String(row.join_time ?? row.attendance_timestamp ?? row.server_timestamp ?? new Date().toISOString()),
    lastHeartbeatTimestamp: breakdown.lastHeartbeatTime,
    totalAcceptedHeartbeats: breakdown.acceptedHeartbeats,
    lastHeartbeatSequenceNumber: breakdown.lastHeartbeatSequence,
    currentHeartbeatStatus: breakdown.lastHeartbeatStatus
  };
}

export async function listStudentAttendanceHistory(studentId: string, limit = 50) {
  const cappedLimit = Math.max(1, Math.min(Number(limit) || 50, 50));
  const result = await pool.query(
    `SELECT a.session_id,
            s.course_name,
            a.status,
            a.join_score,
            a.join_time,
            a.confidence_breakdown
       FROM attendance a
       JOIN sessions s ON s.id = a.session_id
      WHERE a.student_id = $1
      ORDER BY a.join_time DESC
      LIMIT $2`,
    [studentId, cappedLimit]
  );

  return result.rows.map((row) => rowToRecord(row as Record<string, unknown>));
}

export async function getStudentCurrentAttendance(sessionId: string, studentId: string): Promise<StudentCurrentAttendanceRecord | null> {
  const result = await pool.query(
    `SELECT a.session_id,
            s.course_name,
            a.status,
            a.join_score,
            a.join_time,
            a.confidence_breakdown,
            h.status AS current_heartbeat_status,
            h.sequence_number AS current_heartbeat_sequence_number,
            h.server_timestamp AS current_heartbeat_timestamp
       FROM attendance a
       JOIN sessions s ON s.id = a.session_id
  LEFT JOIN LATERAL (
            SELECT status, sequence_number, server_timestamp
              FROM heartbeats
             WHERE session_id = a.session_id
               AND student_id = a.student_id
          ORDER BY sequence_number DESC, server_timestamp DESC
             LIMIT 1
       ) h ON TRUE
      WHERE a.session_id = $1
        AND a.student_id = $2
      LIMIT 1`,
    [sessionId, studentId]
  );

  if ((result.rowCount ?? 0) === 0) {
    return null;
  }

  const row = result.rows[0] as Record<string, unknown>;
  const record = rowToRecord(row);

  return {
    ...record,
    attendanceTimestamp: String(row.join_time ?? new Date().toISOString()),
    lastHeartbeatTimestamp: typeof row.current_heartbeat_timestamp === 'string' ? row.current_heartbeat_timestamp : record.lastHeartbeatTimestamp,
    totalAcceptedHeartbeats: parseBreakdown(row.confidence_breakdown).acceptedHeartbeats,
    lastHeartbeatSequenceNumber: row.current_heartbeat_sequence_number === null || row.current_heartbeat_sequence_number === undefined ? record.lastHeartbeatSequenceNumber : Number(row.current_heartbeat_sequence_number),
    currentHeartbeatStatus: typeof row.current_heartbeat_status === 'string' ? row.current_heartbeat_status : record.currentHeartbeatStatus,
    confidenceStatus: 'Awaiting teacher fingerprint validation'
  };
}