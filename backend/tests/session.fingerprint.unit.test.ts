jest.mock('../src/config/db', () => {
  const client = {
    query: jest.fn(),
    release: jest.fn()
  };

  return {
    pool: {
      connect: jest.fn(async () => client),
      query: jest.fn()
    }
  };
});

import { pool } from '../src/config/db';
import {
  assertSessionIsActive,
  assertTeacherOwnership,
  storeSessionReferenceFingerprint,
  validateReferenceFingerprintData
} from '../src/services/sessionFingerprintService';

const mockedPool = pool as unknown as { connect: jest.Mock; query: jest.Mock };

describe('session fingerprint service', () => {
  beforeEach(() => {
    mockedPool.connect.mockReset();
    mockedPool.query.mockReset();
  });

  it('validates fingerprint payload structure', () => {
    expect(() => validateReferenceFingerprintData([])).toThrow('INVALID_FINGERPRINT_PAYLOAD');
    expect(() => validateReferenceFingerprintData([{ bssid: 'bad', rssi: -55 }])).toThrow('INVALID_BSSID_FORMAT');
    expect(() => validateReferenceFingerprintData([{ bssid: 'AA:BB:CC:DD:EE:FF', rssi: -101 }])).toThrow('INVALID_RSSI_RANGE');
  });

  it('enforces ownership and active session constraints', () => {
    expect(() => assertTeacherOwnership({ teacher_id: 'teacher-1' } as any, 'teacher-2')).toThrow('SESSION_OWNERSHIP_MISMATCH');
    expect(() => assertSessionIsActive({ status: 'CLOSED' } as any)).toThrow('SESSION_NOT_ACTIVE');
  });

  it('upserts reference fingerprint rows while preserving created_at', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', teacher_id: 'teacher-1', status: 'ACTIVE' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ session_id: 'session-1', teacher_id: 'teacher-1', captured_at: '2026-06-23T10:00:00.000Z', fingerprint_data: [{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Lab', rssi: -50 }], created_at: '2026-06-23T09:00:00.000Z' }] }),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);

    const result = await storeSessionReferenceFingerprint('session-1', 'teacher-1', [
      { bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Lab', rssi: -50 }
    ]);

    expect(result.session_id).toBe('session-1');
    expect(result.teacher_id).toBe('teacher-1');
    expect(result.created_at).toBe('2026-06-23T09:00:00.000Z');
    expect(client.query).toHaveBeenCalledWith(expect.stringContaining('ON CONFLICT (session_id)'), expect.any(Array));
  });
});