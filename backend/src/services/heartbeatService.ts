import type { PoolClient } from 'pg';
import { pool } from '../config/db';
import { computeConfidence } from './confidenceService';
import { matchFingerprint } from './matchingService';
import { logSecurityEvent } from '../auth/securityLogger';
import { shouldRejectHeartbeatForAttendanceStatus } from './sessionService';

export type WifiFingerprintEntry = {
  bssid: string;
  ssid?: string | null;
  rssi: number;
};

export type HeartbeatInput = {
  sessionId: string;
  wifiFingerprint: WifiFingerprintEntry[];
  sequenceNumber: number;
  deviceFingerprint: string;
  timestamp: string | Date;
};

type RollingTokenRow = {
  id: string;
  session_id: string;
  sequence_number: number;
  token_hash: string;
  valid_from: string;
  valid_to: string;
};

type SessionRow = {
  id: string;
  classroom_id: string | null;
  status: string;
};

type AttendanceRow = {
  id: string;
  session_id: string;
  student_id: string;
  confidence_score: number | null;
  confidence_breakdown: unknown;
  status: string;
};

type DeviceBindingRow = {
  device_fingerprint: unknown;
};

type HeartbeatPersistedRow = {
  id: string;
  sessionId: string;
  studentId: string;
  sequenceNumber: number;
  confidenceScore: number | null;
  classificationResult: 'INSIDE_CLASSROOM' | 'OUTSIDE_CLASSROOM' | null;
  status: 'ACCEPTED' | 'REJECTED';
  rejectionReason: string | null;
  serverTimestamp: string;
};

type HeartbeatOutcome =
  | {
      accepted: true;
      statusCode: 200;
      heartbeat: HeartbeatPersistedRow;
      runningPresenceScore: number;
      confidenceScore: number;
      classificationResult: 'INSIDE_CLASSROOM' | 'OUTSIDE_CLASSROOM';
    }
  | {
      accepted: false;
      statusCode: number;
      errorCode: string;
      message: string;
      heartbeat?: HeartbeatPersistedRow | null;
    };

const BSSID_REGEX = /^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$/;
const MAX_WIFI_ENTRIES = 20;

function createError(message: string, statusCode: number) {
  const error = new Error(message);
  (error as any).statusCode = statusCode;
  return error;
}

function normalizeTimestamp(timestamp: string | Date) {
  const parsed = timestamp instanceof Date ? timestamp : new Date(timestamp);
  if (Number.isNaN(parsed.getTime())) {
    throw createError('INVALID_TIMESTAMP', 400);
  }
  return parsed;
}

export function validateSequenceProgress(lastAcceptedSequence: number, sequenceNumber: number) {
  if (!Number.isInteger(sequenceNumber) || sequenceNumber < 1) {
    throw createError('INVALID_SEQUENCE_NUMBER', 400);
  }

  if (sequenceNumber <= lastAcceptedSequence) {
    return {
      accepted: false,
      errorCode: 'SEQUENCE_OUT_OF_ORDER',
      statusCode: 400,
      message: 'Received sequence number is not greater than the last accepted sequence.'
    } as const;
  }

  if (lastAcceptedSequence > 0 && sequenceNumber > lastAcceptedSequence + 1) {
    return {
      accepted: true,
      gapFrom: lastAcceptedSequence + 1,
      gapTo: sequenceNumber - 1
    } as const;
  }

  return { accepted: true } as const;
}

export function calculateRunningPresenceScore(previousRunningPresenceScore: number, previousAcceptedHeartbeats: number, newConfidenceScore: number) {
  const acceptedCount = Number.isFinite(previousAcceptedHeartbeats) && previousAcceptedHeartbeats > 0 ? previousAcceptedHeartbeats : 0;
  const previousScore = Number.isFinite(previousRunningPresenceScore) && previousRunningPresenceScore > 0 ? previousRunningPresenceScore : 0;

  if (acceptedCount === 0) {
    return Math.max(0, Math.min(100, Math.round(newConfidenceScore)));
  }

  const nextScore = ((previousScore * acceptedCount) + newConfidenceScore) / (acceptedCount + 1);
  return Math.max(0, Math.min(100, Math.round(nextScore)));
}

export function normalizeWifiFingerprint(input: unknown) {
  if (!Array.isArray(input)) {
    throw createError('INVALID_WIFI_FINGERPRINT', 400);
  }

  if (input.length > MAX_WIFI_ENTRIES) {
    throw createError('WIFI_FINGERPRINT_TOO_LARGE', 400);
  }

  const normalized: WifiFingerprintEntry[] = [];

  for (const entry of input) {
    if (!entry || typeof entry !== 'object') {
      throw createError('INVALID_WIFI_FINGERPRINT', 400);
    }

    const candidate = entry as WifiFingerprintEntry;
    const bssid = typeof candidate.bssid === 'string' ? candidate.bssid.trim() : '';
    const ssid = typeof candidate.ssid === 'string' ? candidate.ssid.trim() : candidate.ssid ?? null;
    const rssi = typeof candidate.rssi === 'number' ? candidate.rssi : Number(candidate.rssi);

    if (!BSSID_REGEX.test(bssid)) {
      throw createError('INVALID_BSSID', 400);
    }

    if (typeof ssid === 'string' && ssid.length > 32) {
      throw createError('INVALID_SSID', 400);
    }

    if (!Number.isFinite(rssi) || !Number.isInteger(rssi) || rssi < -100 || rssi > 0) {
      throw createError('INVALID_RSSI', 400);
    }

    normalized.push({
      bssid,
      ssid: ssid ?? null,
      rssi
    });
  }

  return normalized;
}

function fingerprintToVector(fingerprint: WifiFingerprintEntry[]) {
  return fingerprint.reduce<Record<string, number>>((accumulator, item) => {
    accumulator[item.bssid] = item.rssi;
    return accumulator;
  }, {});
}

function parseBreakdown(breakdown: unknown) {
  if (!breakdown || typeof breakdown !== 'object') {
    return {
      runningPresenceScore: 0,
      acceptedHeartbeats: 0,
      rejectedHeartbeats: 0,
      lastHeartbeatSequence: null as number | null,
      lastHeartbeatStatus: null as string | null,
      lastHeartbeatTime: null as string | null,
      lastConfidenceScore: null as number | null,
      lastClassificationResult: null as string | null
    };
  }

  const record = breakdown as Record<string, unknown>;
  return {
    runningPresenceScore: Number(record.runningPresenceScore ?? 0),
    acceptedHeartbeats: Number(record.acceptedHeartbeats ?? 0),
    rejectedHeartbeats: Number(record.rejectedHeartbeats ?? 0),
    lastHeartbeatSequence: record.lastHeartbeatSequence === null || record.lastHeartbeatSequence === undefined ? null : Number(record.lastHeartbeatSequence),
    lastHeartbeatStatus: typeof record.lastHeartbeatStatus === 'string' ? record.lastHeartbeatStatus : null,
    lastHeartbeatTime: typeof record.lastHeartbeatTime === 'string' ? record.lastHeartbeatTime : null,
    lastConfidenceScore: record.lastConfidenceScore === null || record.lastConfidenceScore === undefined ? null : Number(record.lastConfidenceScore),
    lastClassificationResult: typeof record.lastClassificationResult === 'string' ? record.lastClassificationResult : null
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
      // Preserve the original failure signal.
    }
    throw error;
  } finally {
    client.release();
  }
}

async function loadSession(client: PoolClient, sessionId: string) {
  const result = await client.query<SessionRow>(`SELECT id, classroom_id, status FROM sessions WHERE id = $1 LIMIT 1`, [sessionId]);
  return result.rowCount ? result.rows[0] : null;
}

async function loadAttendance(client: PoolClient, sessionId: string, studentId: string) {
  const result = await client.query<AttendanceRow>(
    `SELECT id, session_id, student_id, confidence_score, confidence_breakdown, status
       FROM attendance
      WHERE session_id = $1 AND student_id = $2
      LIMIT 1`,
    [sessionId, studentId]
  );
  return result.rowCount ? result.rows[0] : null;
}

async function loadActiveDeviceBindings(client: PoolClient, studentId: string) {
  const result = await client.query<DeviceBindingRow>(
    `SELECT device_fingerprint
       FROM device_bindings
      WHERE user_id = $1
        AND revoked = false
      ORDER BY created_at DESC`,
    [studentId]
  );
  return result.rows;
}

function deviceBindingMatches(binding: DeviceBindingRow, deviceFingerprint: string) {
  if (binding.device_fingerprint === null || binding.device_fingerprint === undefined) {
    return false;
  }

  if (typeof binding.device_fingerprint === 'string') {
    return binding.device_fingerprint === deviceFingerprint;
  }

  try {
    const serialized = JSON.stringify(binding.device_fingerprint);
    return serialized === deviceFingerprint || serialized.includes(`"hash":"${deviceFingerprint}"`);
  } catch (_error) {
    return false;
  }
}

async function validateDeviceBinding(client: PoolClient, studentId: string, deviceFingerprint: string) {
  const activeBindings = await loadActiveDeviceBindings(client, studentId);
  if (activeBindings.length === 0) {
    return {
      accepted: false as const,
      statusCode: 403,
      errorCode: 'NO_DEVICE_BINDING',
      message: 'No active device binding found for this student.'
    };
  }

  const hasMatch = activeBindings.some((binding) => deviceBindingMatches(binding, deviceFingerprint));
  if (!hasMatch) {
    await logSecurityEvent(studentId, 'device_binding_mismatch', {
      deviceFingerprint,
      activeBindingCount: activeBindings.length
    });
  }

  return { accepted: true as const };
}

async function loadActiveRollingToken(client: PoolClient, sessionId: string, clientTimestamp: Date) {
  const result = await client.query<RollingTokenRow>(
    `SELECT id, session_id, sequence_number, token_hash, valid_from, valid_to
       FROM rolling_tokens
      WHERE session_id = $1
        AND valid_from <= $2
        AND valid_to >= $2
      ORDER BY sequence_number DESC
      LIMIT 1`,
    [sessionId, clientTimestamp.toISOString()]
  );
  return result.rowCount ? result.rows[0] : null;
}

async function loadLastAcceptedSequence(client: PoolClient, sessionId: string, studentId: string) {
  const result = await client.query<{ seq_no: number }>(
    `SELECT seq_no
       FROM heartbeats
      WHERE session_id = $1
        AND student_id = $2
        AND status = 'ACCEPTED'
      ORDER BY seq_no DESC
      LIMIT 1`,
    [sessionId, studentId]
  );
  return result.rowCount ? Number(result.rows[0].seq_no) : 0;
}

async function recordSequenceGap(client: PoolClient, sessionId: string, studentId: string, gapFrom: number, gapTo: number) {
  if (gapTo < gapFrom) {
    return;
  }

  await client.query(
    `INSERT INTO sequence_gaps (session_id, student_id, gap_from, gap_to, detected_at)
     VALUES ($1, $2, $3, $4, now())`,
    [sessionId, studentId, gapFrom, gapTo]
  );
}

async function storeHeartbeat(client: PoolClient, params: {
  sessionId: string;
  studentId: string;
  sequenceNumber: number;
  tokenHmac: string | null;
  fingerprintData: WifiFingerprintEntry[];
  deviceFingerprint: string;
  clientTimestamp: Date;
  status: 'ACCEPTED' | 'REJECTED';
  rejectionReason: string | null;
  confidenceScore: number | null;
  classificationResult: 'INSIDE_CLASSROOM' | 'OUTSIDE_CLASSROOM' | null;
}) {
  const result = await client.query<{ id: string; server_ts: string }>(
    `INSERT INTO heartbeats (
      session_id,
      student_id,
      seq_no,
      token_hmac,
      fingerprint_data,
      device_fingerprint,
      client_ts,
      server_ts,
      status,
      rejection_reason,
      confidence_score,
      classification_result
    ) VALUES ($1, $2, $3, $4, $5, $6, $7, now(), $8, $9, $10, $11)
    RETURNING id, server_ts`,
    [
      params.sessionId,
      params.studentId,
      params.sequenceNumber,
      params.tokenHmac,
      JSON.stringify(params.fingerprintData),
      params.deviceFingerprint,
      params.clientTimestamp.toISOString(),
      params.status,
      params.rejectionReason,
      params.confidenceScore,
      params.classificationResult
    ]
  );

  return {
    id: result.rows[0].id,
    serverTimestamp: result.rows[0].server_ts
  };
}

async function updateAttendanceProgress(client: PoolClient, attendance: AttendanceRow, params: {
  accepted: boolean;
  heartbeatSequence: number;
  heartbeatScore: number | null;
  classificationResult: 'INSIDE_CLASSROOM' | 'OUTSIDE_CLASSROOM' | null;
  heartbeatTime: string;
}) {
  const breakdown = parseBreakdown(attendance.confidence_breakdown);
  const acceptedHeartbeats = breakdown.acceptedHeartbeats + (params.accepted ? 1 : 0);
  const rejectedHeartbeats = breakdown.rejectedHeartbeats + (params.accepted ? 0 : 1);
  const runningPresenceScore = params.accepted
    ? calculateRunningPresenceScore(breakdown.runningPresenceScore, breakdown.acceptedHeartbeats, params.heartbeatScore ?? 0)
    : breakdown.runningPresenceScore;

  const nextBreakdown = {
    ...breakdown,
    runningPresenceScore,
    acceptedHeartbeats,
    rejectedHeartbeats,
    lastHeartbeatSequence: params.heartbeatSequence,
    lastHeartbeatStatus: params.accepted ? 'ACCEPTED' : 'REJECTED',
    lastHeartbeatTime: params.heartbeatTime,
    lastConfidenceScore: params.heartbeatScore,
    lastClassificationResult: params.classificationResult
  };

  await client.query(
    `UPDATE attendance
        SET confidence_score = $1,
            confidence_breakdown = $2,
            updated_at = now()
      WHERE id = $3`,
    [runningPresenceScore, JSON.stringify(nextBreakdown), attendance.id]
  );

  return { runningPresenceScore, acceptedHeartbeats, rejectedHeartbeats };
}

async function classifyHeartbeat(sessionClassroomId: string | null, wifiFingerprint: WifiFingerprintEntry[]) {
  if (wifiFingerprint.length === 0) {
    return {
      confidenceScore: 0,
      classificationResult: 'OUTSIDE_CLASSROOM' as const
    };
  }

  const { neighbors } = await matchFingerprint(fingerprintToVector(wifiFingerprint));
  const confidence = computeConfidence(neighbors);
  const classificationResult = confidence.classroom_id && confidence.classroom_id === sessionClassroomId && confidence.score > 0
    ? ('INSIDE_CLASSROOM' as const)
    : ('OUTSIDE_CLASSROOM' as const);

  return {
    confidenceScore: confidence.score,
    classificationResult
  };
}

async function persistRejectedHeartbeat(client: PoolClient, params: {
  sessionId: string;
  studentId: string;
  sequenceNumber: number;
  tokenHmac: string | null;
  fingerprintData: WifiFingerprintEntry[];
  deviceFingerprint: string;
  clientTimestamp: Date;
  reason: string;
}) {
  return storeHeartbeat(client, {
    sessionId: params.sessionId,
    studentId: params.studentId,
    sequenceNumber: params.sequenceNumber,
    tokenHmac: params.tokenHmac,
    fingerprintData: params.fingerprintData,
    deviceFingerprint: params.deviceFingerprint,
    clientTimestamp: params.clientTimestamp,
    status: 'REJECTED',
    rejectionReason: params.reason,
    confidenceScore: null,
    classificationResult: null
  });
}

export async function submitHeartbeat(studentId: string, input: HeartbeatInput): Promise<HeartbeatOutcome> {
  const trimmedStudentId = studentId?.trim();
  const sessionId = input.sessionId?.trim();
  const deviceFingerprint = input.deviceFingerprint?.trim();
  const clientTimestamp = normalizeTimestamp(input.timestamp);
  const sequenceNumber = Number(input.sequenceNumber);

  if (!trimmedStudentId || !sessionId || !deviceFingerprint) {
    throw createError('MISSING_REQUIRED_FIELDS', 400);
  }

  if (!Number.isInteger(sequenceNumber) || sequenceNumber < 1) {
    throw createError('INVALID_SEQUENCE_NUMBER', 400);
  }

  const wifiFingerprint = normalizeWifiFingerprint(input.wifiFingerprint);

  return runInTransaction(async (client) => {
    const session = await loadSession(client, sessionId);
    if (!session) {
      return {
        accepted: false,
        statusCode: 404,
        errorCode: 'SESSION_NOT_FOUND',
        message: 'Session does not exist.'
      } satisfies HeartbeatOutcome;
    }

    const attendance = await loadAttendance(client, sessionId, trimmedStudentId);
    if (!attendance) {
      await persistRejectedHeartbeat(client, {
        sessionId,
        studentId: trimmedStudentId,
        sequenceNumber,
        tokenHmac: null,
        fingerprintData: wifiFingerprint,
        deviceFingerprint,
        clientTimestamp,
        reason: 'SESSION_NOT_JOINED'
      });
      return {
        accepted: false,
        statusCode: 403,
        errorCode: 'SESSION_NOT_JOINED',
        message: 'Student has not joined this session.'
      } satisfies HeartbeatOutcome;
    }

    const deviceValidation = await validateDeviceBinding(client, trimmedStudentId, deviceFingerprint);
    if (!deviceValidation.accepted) {
      await persistRejectedHeartbeat(client, {
        sessionId,
        studentId: trimmedStudentId,
        sequenceNumber,
        tokenHmac: null,
        fingerprintData: wifiFingerprint,
        deviceFingerprint,
        clientTimestamp,
        reason: deviceValidation.errorCode
      });
      return {
        accepted: false,
        statusCode: deviceValidation.statusCode,
        errorCode: deviceValidation.errorCode,
        message: deviceValidation.message
      } satisfies HeartbeatOutcome;
    }

    if (shouldRejectHeartbeatForAttendanceStatus(attendance.status)) {
      await persistRejectedHeartbeat(client, {
        sessionId,
        studentId: trimmedStudentId,
        sequenceNumber,
        tokenHmac: null,
        fingerprintData: wifiFingerprint,
        deviceFingerprint,
        clientTimestamp,
        reason: 'ATTENDANCE_REJECTED'
      });
      return {
        accepted: false,
        statusCode: 403,
        errorCode: 'ATTENDANCE_REJECTED',
        message: 'Attendance status is REJECTED for this student.'
      } satisfies HeartbeatOutcome;
    }

    if (session.status !== 'ACTIVE') {
      await persistRejectedHeartbeat(client, {
        sessionId,
        studentId: trimmedStudentId,
        sequenceNumber,
        tokenHmac: null,
        fingerprintData: wifiFingerprint,
        deviceFingerprint,
        clientTimestamp,
        reason: 'SESSION_NOT_ACTIVE'
      });
      return {
        accepted: false,
        statusCode: 409,
        errorCode: 'SESSION_NOT_ACTIVE',
        message: 'Session is not ACTIVE.'
      } satisfies HeartbeatOutcome;
    }

    const rollingToken = await loadActiveRollingToken(client, sessionId, clientTimestamp);
    if (!rollingToken) {
      await persistRejectedHeartbeat(client, {
        sessionId,
        studentId: trimmedStudentId,
        sequenceNumber,
        tokenHmac: null,
        fingerprintData: wifiFingerprint,
        deviceFingerprint,
        clientTimestamp,
        reason: 'ROLLING_TOKEN_INVALID'
      });
      return {
        accepted: false,
        statusCode: 401,
        errorCode: 'ROLLING_TOKEN_INVALID',
        message: 'No active rolling token is valid for this heartbeat timestamp.'
      } satisfies HeartbeatOutcome;
    }

    const lastAcceptedSequence = await loadLastAcceptedSequence(client, sessionId, trimmedStudentId);
    const sequenceProgress = validateSequenceProgress(lastAcceptedSequence, sequenceNumber);
    if (!sequenceProgress.accepted) {
      await persistRejectedHeartbeat(client, {
        sessionId,
        studentId: trimmedStudentId,
        sequenceNumber,
        tokenHmac: rollingToken.token_hash,
        fingerprintData: wifiFingerprint,
        deviceFingerprint,
        clientTimestamp,
        reason: sequenceProgress.errorCode
      });
      await updateAttendanceProgress(client, attendance, {
        accepted: false,
        heartbeatSequence: sequenceNumber,
        heartbeatScore: null,
        classificationResult: null,
        heartbeatTime: clientTimestamp.toISOString()
      });
      return {
        accepted: false,
        statusCode: sequenceProgress.statusCode,
        errorCode: sequenceProgress.errorCode,
        message: sequenceProgress.message
      } satisfies HeartbeatOutcome;
    }

    if ('gapFrom' in sequenceProgress && typeof sequenceProgress.gapFrom === 'number' && typeof sequenceProgress.gapTo === 'number') {
      await recordSequenceGap(client, sessionId, trimmedStudentId, sequenceProgress.gapFrom, sequenceProgress.gapTo);
    }

    const classification = await classifyHeartbeat(session.classroom_id, wifiFingerprint);
    const persistedHeartbeat = await storeHeartbeat(client, {
      sessionId,
      studentId: trimmedStudentId,
      sequenceNumber,
      tokenHmac: rollingToken.token_hash,
      fingerprintData: wifiFingerprint,
      deviceFingerprint,
      clientTimestamp,
      status: 'ACCEPTED',
      rejectionReason: null,
      confidenceScore: classification.confidenceScore,
      classificationResult: classification.classificationResult
    });

    const progress = await updateAttendanceProgress(client, attendance, {
      accepted: true,
      heartbeatSequence: sequenceNumber,
      heartbeatScore: classification.confidenceScore,
      classificationResult: classification.classificationResult,
      heartbeatTime: clientTimestamp.toISOString()
    });

    return {
      accepted: true,
      statusCode: 200,
      heartbeat: {
        id: persistedHeartbeat.id,
        sessionId,
        studentId: trimmedStudentId,
        sequenceNumber,
        confidenceScore: classification.confidenceScore,
        classificationResult: classification.classificationResult,
        status: 'ACCEPTED',
        rejectionReason: null,
        serverTimestamp: persistedHeartbeat.serverTimestamp
      },
      runningPresenceScore: progress.runningPresenceScore,
      confidenceScore: classification.confidenceScore,
      classificationResult: classification.classificationResult
    } satisfies HeartbeatOutcome;
  });
}
