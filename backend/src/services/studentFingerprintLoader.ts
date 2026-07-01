import { pool } from '../config/db';
import type { WifiFingerprintEntry } from './heartbeatService';

type HeartbeatFingerprintRow = {
  fingerprint_data: unknown;
  server_ts: string;
};

const BSSID_REGEX = /^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$/;

function normalizeHeartbeatFingerprint(fingerprintData: unknown): WifiFingerprintEntry[] {
  if (!Array.isArray(fingerprintData)) {
    return [];
  }

  const deduped = new Map<string, WifiFingerprintEntry>();

  for (const entry of fingerprintData) {
    if (!entry || typeof entry !== 'object') {
      continue;
    }

    const candidate = entry as Record<string, unknown>;
    const bssid = typeof candidate.bssid === 'string' ? candidate.bssid.trim().toUpperCase() : '';
    const ssid = typeof candidate.ssid === 'string' ? candidate.ssid.trim() : null;
    const rssi = Number(candidate.rssi);

    if (!BSSID_REGEX.test(bssid)) {
      continue;
    }

    if (!Number.isInteger(rssi) || rssi < -100 || rssi > 0) {
      continue;
    }

    const normalized: WifiFingerprintEntry = {
      bssid,
      ssid,
      rssi
    };

    const previous = deduped.get(bssid);
    if (!previous || normalized.rssi > previous.rssi) {
      deduped.set(bssid, normalized);
    }
  }

  return Array.from(deduped.values());
}

export type StudentFingerprintLoadResult = {
  sessionId: string;
  studentId: string;
  status: 'FOUND' | 'MISSING_HEARTBEAT';
  fingerprintData: WifiFingerprintEntry[];
  heartbeatTimestamp: string | null;
};

export async function loadLatestStudentFingerprintForSession(
  sessionId: string,
  studentId: string
): Promise<StudentFingerprintLoadResult> {
  const normalizedSessionId = sessionId?.trim();
  const normalizedStudentId = studentId?.trim();

  if (!normalizedSessionId || !normalizedStudentId) {
    throw new Error('MISSING_REQUIRED_FIELDS');
  }

  const result = await pool.query<HeartbeatFingerprintRow>(
    `SELECT fingerprint_data, server_ts
       FROM heartbeats
      WHERE session_id = $1
        AND student_id = $2
      ORDER BY server_ts DESC
      LIMIT 1`,
    [normalizedSessionId, normalizedStudentId]
  );

  if ((result.rowCount ?? 0) === 0) {
    return {
      sessionId: normalizedSessionId,
      studentId: normalizedStudentId,
      status: 'MISSING_HEARTBEAT',
      fingerprintData: [],
      heartbeatTimestamp: null
    };
  }

  const row = result.rows[0];
  const normalizedFingerprint = normalizeHeartbeatFingerprint(row.fingerprint_data);

  return {
    sessionId: normalizedSessionId,
    studentId: normalizedStudentId,
    status: normalizedFingerprint.length > 0 ? 'FOUND' : 'MISSING_HEARTBEAT',
    fingerprintData: normalizedFingerprint,
    heartbeatTimestamp: row.server_ts
  };
}

export { normalizeHeartbeatFingerprint };
