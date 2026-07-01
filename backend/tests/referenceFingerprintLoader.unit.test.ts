jest.mock('../src/config/db', () => ({
  pool: {
    query: jest.fn()
  }
}));

jest.mock('../src/services/sessionFingerprintService', () => ({
  getSessionReferenceFingerprint: jest.fn()
}));

import { pool } from '../src/config/db';
import { getSessionReferenceFingerprint } from '../src/services/sessionFingerprintService';
import { loadReferenceFingerprintForSession } from '../src/services/referenceFingerprintLoader';

const mockedPool = pool as unknown as { query: jest.Mock };
const mockedGetSessionReferenceFingerprint = getSessionReferenceFingerprint as jest.MockedFunction<typeof getSessionReferenceFingerprint>;

describe('reference fingerprint loader', () => {
  beforeEach(() => {
    mockedPool.query.mockReset();
    mockedGetSessionReferenceFingerprint.mockReset();
  });

  it('loads teacher reference fingerprint for a valid session', async () => {
    mockedPool.query.mockResolvedValueOnce({ rowCount: 1, rows: [{ teacher_id: 'teacher-1' }] });
    mockedGetSessionReferenceFingerprint.mockResolvedValueOnce({
      session_id: 'session-1',
      teacher_id: 'teacher-1',
      captured_at: '2026-07-01T09:00:00.000Z',
      created_at: '2026-07-01T09:00:00.000Z',
      fingerprint_data: [{ bssid: 'AA:BB:CC:DD:EE:FF', rssi: -55, ssid: 'Lab' }]
    } as any);

    const result = await loadReferenceFingerprintForSession('session-1');

    expect(result.status).toBe('FOUND');
    expect(result.teacherId).toBe('teacher-1');
    expect(result.fingerprintData).toHaveLength(1);
    expect(mockedGetSessionReferenceFingerprint).toHaveBeenCalledWith('session-1', 'teacher-1');
  });

  it('returns session not found when session does not exist', async () => {
    mockedPool.query.mockResolvedValueOnce({ rowCount: 0, rows: [] });

    const result = await loadReferenceFingerprintForSession('session-missing');

    expect(result.status).toBe('SESSION_NOT_FOUND');
    expect(result.fingerprintData).toEqual([]);
  });

  it('returns missing reference when fingerprint is not captured yet', async () => {
    mockedPool.query.mockResolvedValueOnce({ rowCount: 1, rows: [{ teacher_id: 'teacher-1' }] });
    mockedGetSessionReferenceFingerprint.mockResolvedValueOnce(null);

    const result = await loadReferenceFingerprintForSession('session-1');

    expect(result.status).toBe('MISSING_REFERENCE');
    expect(result.fingerprintData).toEqual([]);
  });
});
