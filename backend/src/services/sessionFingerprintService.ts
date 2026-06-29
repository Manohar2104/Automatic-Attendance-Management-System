import { pool } from '../config/db';
import type { PoolClient } from 'pg';

export type ReferenceFingerprintAccessPoint = {
  bssid: string;
  ssid?: string | null;
  rssi: number;
};

export type SessionReferenceFingerprintRow = {
  session_id: string;
  teacher_id: string;
  captured_at: string;
  fingerprint_data: ReferenceFingerprintAccessPoint[];
  created_at: string;
};

type SessionOwnershipRow = {
  id: string;
  teacher_id: string;
  status: string;
};

function createHttpError(message: string, statusCode: number) {
  const error = new Error(message);
  (error as any).statusCode = statusCode;
  return error;
}

function normalizeFingerprintRow(row: any): SessionReferenceFingerprintRow {
  return {
    session_id: String(row.session_id),
    teacher_id: String(row.teacher_id),
    captured_at: String(row.captured_at),
    fingerprint_data: Array.isArray(row.fingerprint_data) ? row.fingerprint_data : [],
    created_at: String(row.created_at)
  };
}

export function validateReferenceFingerprintData(fingerprintData: unknown): ReferenceFingerprintAccessPoint[] {
  if (!Array.isArray(fingerprintData) || fingerprintData.length === 0) {
    throw createHttpError('INVALID_FINGERPRINT_PAYLOAD', 400);
  }

  return fingerprintData.map((entry, index) => {
    if (!entry || typeof entry !== 'object') {
      throw createHttpError(`INVALID_FINGERPRINT_ENTRY_${index}`, 400);
    }

    const accessPoint = entry as Record<string, unknown>;
    const bssid = typeof accessPoint.bssid === 'string' ? accessPoint.bssid.trim() : '';
    const ssid = typeof accessPoint.ssid === 'string' ? accessPoint.ssid.trim() : null;
    const rssi = Number(accessPoint.rssi);

    if (!/^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$/.test(bssid)) {
      throw createHttpError('INVALID_BSSID_FORMAT', 400);
    }

    if (!Number.isInteger(rssi) || rssi < -100 || rssi > 0) {
      throw createHttpError('INVALID_RSSI_RANGE', 400);
    }

    return {
      bssid,
      ssid: ssid && ssid.length > 0 ? ssid : null,
      rssi
    };
  });
}

async function getSessionOwnership(client: PoolClient, sessionId: string): Promise<SessionOwnershipRow | null> {
  const result = await client.query(
    `SELECT id, teacher_id, status
       FROM sessions
      WHERE id = $1
      LIMIT 1`,
    [sessionId]
  );

  return (result.rowCount ?? 0) === 0 ? null : (result.rows[0] as SessionOwnershipRow);
}

function assertTeacherOwnership(session: SessionOwnershipRow, teacherId: string) {
  if (session.teacher_id !== teacherId) {
    throw createHttpError('SESSION_OWNERSHIP_MISMATCH', 403);
  }
}

function assertSessionIsActive(session: SessionOwnershipRow) {
  if (String(session.status).toLowerCase() !== 'active') {
    throw createHttpError('SESSION_NOT_ACTIVE', 409);
  }
}

export async function storeSessionReferenceFingerprint(
  sessionId: string,
  teacherId: string,
  fingerprintData: unknown
): Promise<SessionReferenceFingerprintRow> {
  const trimmedSessionId = sessionId?.trim();
  const trimmedTeacherId = teacherId?.trim();

  if (!trimmedSessionId || !trimmedTeacherId) {
    throw createHttpError('MISSING_REQUIRED_FIELDS', 400);
  }

  const normalizedFingerprintData = validateReferenceFingerprintData(fingerprintData);

  const client = await pool.connect();
  try {
    const session = await getSessionOwnership(client, trimmedSessionId);
    if (!session) {
      throw createHttpError('SESSION_NOT_FOUND', 404);
    }

    assertTeacherOwnership(session, trimmedTeacherId);
    assertSessionIsActive(session);

    const result = await client.query(
      `INSERT INTO session_reference_fingerprints (
         session_id,
         teacher_id,
         captured_at,
         fingerprint_data,
         created_at
       ) VALUES ($1, $2, NOW(), $3, NOW())
       ON CONFLICT (session_id)
       DO UPDATE SET
         teacher_id = EXCLUDED.teacher_id,
         captured_at = EXCLUDED.captured_at,
         fingerprint_data = EXCLUDED.fingerprint_data
       RETURNING session_id, teacher_id, captured_at, fingerprint_data, created_at`,
      [trimmedSessionId, trimmedTeacherId, JSON.stringify(normalizedFingerprintData)]
    );

    return normalizeFingerprintRow(result.rows[0]);
  } finally {
    client.release();
  }
}

export async function getSessionReferenceFingerprint(sessionId: string, teacherId: string): Promise<SessionReferenceFingerprintRow | null> {
  const trimmedSessionId = sessionId?.trim();
  const trimmedTeacherId = teacherId?.trim();

  if (!trimmedSessionId || !trimmedTeacherId) {
    throw createHttpError('MISSING_REQUIRED_FIELDS', 400);
  }

  const client = await pool.connect();
  try {
    const session = await getSessionOwnership(client, trimmedSessionId);
    if (!session) {
      throw createHttpError('SESSION_NOT_FOUND', 404);
    }

    assertTeacherOwnership(session, trimmedTeacherId);

    const result = await client.query(
      `SELECT session_id, teacher_id, captured_at, fingerprint_data, created_at
         FROM session_reference_fingerprints
        WHERE session_id = $1
        LIMIT 1`,
      [trimmedSessionId]
    );

    if ((result.rowCount ?? 0) === 0) {
      return null;
    }

    return normalizeFingerprintRow(result.rows[0]);
  } finally {
    client.release();
  }
}

export { assertTeacherOwnership, assertSessionIsActive };