jest.mock('../src/config/db', () => ({
  pool: {
    query: jest.fn()
  }
}));

import { pool } from '../src/config/db';
import {
  loadLatestStudentFingerprintForSession,
  normalizeHeartbeatFingerprint
} from '../src/services/studentFingerprintLoader';

const mockedPool = pool as unknown as { query: jest.Mock };

describe('student fingerprint loader', () => {
  beforeEach(() => {
    mockedPool.query.mockReset();
  });

  it('loads latest student heartbeat fingerprint', async () => {
    mockedPool.query.mockResolvedValueOnce({
      rowCount: 1,
      rows: [{
        fingerprint_data: [{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Lab', rssi: -60 }],
        server_ts: '2026-07-01T10:00:00.000Z'
      }]
    });

    const result = await loadLatestStudentFingerprintForSession('session-1', 'student-1');

    expect(result.status).toBe('FOUND');
    expect(result.fingerprintData).toHaveLength(1);
    expect(result.heartbeatTimestamp).toBe('2026-07-01T10:00:00.000Z');
  });

  it('returns missing heartbeat when no heartbeat row exists', async () => {
    mockedPool.query.mockResolvedValueOnce({ rowCount: 0, rows: [] });

    const result = await loadLatestStudentFingerprintForSession('session-1', 'student-1');

    expect(result.status).toBe('MISSING_HEARTBEAT');
    expect(result.fingerprintData).toEqual([]);
  });

  it('deduplicates duplicate APs and keeps stronger RSSI', () => {
    const normalized = normalizeHeartbeatFingerprint([
      { bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Lab', rssi: -70 },
      { bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Lab', rssi: -55 }
    ]);

    expect(normalized).toHaveLength(1);
    expect(normalized[0].rssi).toBe(-55);
  });

  it('filters invalid AP entries from heartbeat payload', () => {
    const normalized = normalizeHeartbeatFingerprint([
      { bssid: 'bad', ssid: 'Lab', rssi: -55 },
      { bssid: '11:22:33:44:55:66', ssid: 'Lab', rssi: -58 }
    ]);

    expect(normalized).toHaveLength(1);
    expect(normalized[0].bssid).toBe('11:22:33:44:55:66');
  });
});
