import { pool } from '../config/db';
import {
  getSessionReferenceFingerprint,
  type ReferenceFingerprintAccessPoint
} from './sessionFingerprintService';

export type ReferenceFingerprintLoadResult = {
  sessionId: string;
  teacherId: string | null;
  status: 'FOUND' | 'MISSING_REFERENCE' | 'SESSION_NOT_FOUND';
  fingerprintData: ReferenceFingerprintAccessPoint[];
  capturedAt: string | null;
};

export async function loadReferenceFingerprintForSession(sessionId: string): Promise<ReferenceFingerprintLoadResult> {
  const normalizedSessionId = sessionId?.trim();
  if (!normalizedSessionId) {
    throw new Error('MISSING_SESSION_ID');
  }

  const sessionResult = await pool.query<{ teacher_id: string }>(
    `SELECT teacher_id
       FROM sessions
      WHERE id = $1
      LIMIT 1`,
    [normalizedSessionId]
  );

  if ((sessionResult.rowCount ?? 0) === 0) {
    return {
      sessionId: normalizedSessionId,
      teacherId: null,
      status: 'SESSION_NOT_FOUND',
      fingerprintData: [],
      capturedAt: null
    };
  }

  const teacherId = String(sessionResult.rows[0].teacher_id);
  const reference = await getSessionReferenceFingerprint(normalizedSessionId, teacherId);

  if (!reference) {
    return {
      sessionId: normalizedSessionId,
      teacherId,
      status: 'MISSING_REFERENCE',
      fingerprintData: [],
      capturedAt: null
    };
  }

  return {
    sessionId: normalizedSessionId,
    teacherId,
    status: 'FOUND',
    fingerprintData: reference.fingerprint_data,
    capturedAt: reference.captured_at
  };
}
