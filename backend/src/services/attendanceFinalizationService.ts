import type { PoolClient } from 'pg';
import { pool } from '../config/db';
import { DEFAULT_PRESENCE_THRESHOLD_PRESENT, DEFAULT_PRESENCE_THRESHOLD_PARTIAL } from './sessionService';

export type FinalizationResult = {
  sessionId: string;
  finalized: number;
  skippedRejected: number;
  details: Array<{ studentId: string; finalScore?: number | null; previousStatus: string; newStatus?: string }>;
};

export function computeFinalAttendanceScore(runningPresenceScore: number, joinScore: number) {
  return clampScore((runningPresenceScore * 0.8) + (joinScore * 0.2));
}

export function classifyFinalAttendanceStatus(finalScore: number, presenceThresholdPresent: number, presenceThresholdPartial: number) {
  if (finalScore >= presenceThresholdPresent) {
    return 'PRESENT' as const;
  }

  if (finalScore >= presenceThresholdPartial) {
    return 'PARTIAL' as const;
  }

  return 'ABSENT' as const;
}

function clampScore(n: number) {
  if (!Number.isFinite(n)) return 0;
  return Math.max(0, Math.min(100, Math.round(n)));
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
    } catch (_err) {
      // ignore
    }
    throw error;
  } finally {
    client.release();
  }
}

export async function finalizeAttendanceForSession(sessionId: string): Promise<FinalizationResult> {
  const trimmed = sessionId?.trim();
  if (!trimmed) {
    const err: any = new Error('MISSING_SESSION_ID');
    err.statusCode = 400;
    throw err;
  }

  return runInTransaction(async (client) => {
    const sessionRes = await client.query(
      `SELECT presence_threshold_present, presence_threshold_partial FROM sessions WHERE id = $1 FOR UPDATE`,
      [trimmed]
    );

    if ((sessionRes.rowCount ?? 0) === 0) {
      const err: any = new Error('SESSION_NOT_FOUND');
      err.statusCode = 404;
      throw err;
    }

    const row = sessionRes.rows[0] as { presence_threshold_present: number | null; presence_threshold_partial: number | null };
    const presenceThresholdPresent = Number.isFinite(Number(row.presence_threshold_present)) ? Number(row.presence_threshold_present) : DEFAULT_PRESENCE_THRESHOLD_PRESENT;
    const presenceThresholdPartial = Number.isFinite(Number(row.presence_threshold_partial)) ? Number(row.presence_threshold_partial) : DEFAULT_PRESENCE_THRESHOLD_PARTIAL;

    const attendanceRes = await client.query(
      `SELECT id, student_id, status, join_score, confidence_score, confidence_breakdown, finalized_at
         FROM attendance
        WHERE session_id = $1
        FOR UPDATE`,
      [trimmed]
    );

    const details: FinalizationResult['details'] = [];
    let finalized = 0;
    let skippedRejected = 0;

    for (const rec of attendanceRes.rows) {
      const attendanceId = rec.id as string;
      const studentId = rec.student_id as string;
      const previousStatus = rec.status as string;
      const joinScore = Number.isFinite(Number(rec.join_score)) ? Number(rec.join_score) : 0;
      const finalizedAt = rec.finalized_at as string | null | undefined;

      // Determine running presence score from confidence_score or confidence_breakdown.runningPresenceScore
      let runningPresence = 0;
      if (Number.isFinite(Number(rec.confidence_score))) {
        runningPresence = Number(rec.confidence_score);
      } else if (rec.confidence_breakdown && typeof rec.confidence_breakdown === 'object') {
        try {
          const breakdown = rec.confidence_breakdown as any;
          runningPresence = Number.isFinite(Number(breakdown.runningPresenceScore)) ? Number(breakdown.runningPresenceScore) : 0;
        } catch (_e) {
          runningPresence = 0;
        }
      }

      // If REJECTED, preserve status (do not overwrite)
      if (previousStatus === 'REJECTED') {
        skippedRejected += 1;
        details.push({ studentId, finalScore: null, previousStatus });
        continue;
      }

      if (finalizedAt) {
        details.push({ studentId, finalScore: null, previousStatus, newStatus: previousStatus });
        continue;
      }

      // Compute final score using the approved 80/20 weighted formula.
      const combined = computeFinalAttendanceScore(runningPresence, joinScore);
      const newStatus = classifyFinalAttendanceStatus(combined, presenceThresholdPresent, presenceThresholdPartial);

      await client.query(
        `UPDATE attendance
            SET final_score = $1,
                status = $2,
                finalized_at = now(),
                updated_at = now()
          WHERE id = $3`,
        [combined, newStatus, attendanceId]
      );

      finalized += 1;
      details.push({ studentId, finalScore: combined, previousStatus, newStatus });
    }

    return { sessionId: trimmed, finalized, skippedRejected, details };
  });
}
