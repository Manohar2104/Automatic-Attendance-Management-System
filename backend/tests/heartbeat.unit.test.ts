import {
  calculateRunningPresenceScore,
  normalizeWifiFingerprint,
  maybeRotateRollingToken,
  validateSequenceProgress
} from '../src/services/heartbeatService';

describe('heartbeat service utilities', () => {
  it('rejects duplicate or stale sequence numbers', () => {
    expect(validateSequenceProgress(4, 4).accepted).toBe(false);
    expect(validateSequenceProgress(4, 3).accepted).toBe(false);
  });

  it('detects sequence gaps when sequence jumps forward', () => {
    const result = validateSequenceProgress(4, 7);
    expect(result.accepted).toBe(true);
    expect('gapFrom' in result).toBe(true);
    if ('gapFrom' in result) {
      expect(result.gapFrom).toBe(5);
      expect(result.gapTo).toBe(6);
    }
  });

  it('calculates running presence score as a rolling average', () => {
    expect(calculateRunningPresenceScore(0, 0, 80)).toBe(80);
    expect(calculateRunningPresenceScore(80, 1, 100)).toBe(90);
  });

  it('rotates rolling tokens when the current token is near expiry', async () => {
    const calls: Array<unknown[]> = [];
    const client = {
      query: jest.fn(async (...args: unknown[]) => {
        calls.push(args);

        const sql = String(args[0]);
        if (sql.includes('FROM rolling_tokens') && sql.includes('sequence_number = $2')) {
          return { rowCount: 0, rows: [] };
        }

        if (sql.includes('INSERT INTO rolling_tokens')) {
          return {
            rowCount: 1,
            rows: [{
              id: 'rolling-2',
              session_id: 'session-1',
              sequence_number: 2,
              token_hash: 'hash-2',
              valid_from: '2026-06-16T10:00:45.000Z',
              valid_to: '2026-06-16T10:01:45.000Z'
            }]
          };
        }

        throw new Error(`Unexpected SQL: ${sql}`);
      })
    };

    const rotated = await maybeRotateRollingToken(client as any, {
      id: 'rolling-1',
      session_id: 'session-1',
      sequence_number: 1,
      token_hash: 'hash-1',
      valid_from: '2026-06-16T10:00:00.000Z',
      valid_to: '2026-06-16T10:01:00.000Z'
    }, new Date('2026-06-16T10:00:46.000Z'));

    expect(rotated?.sequence_number).toBe(2);
    expect(calls.length).toBeGreaterThan(0);
  });

  it('validates wifi fingerprint entries', () => {
    expect(() => normalizeWifiFingerprint([{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Room', rssi: -55 }])).not.toThrow();
    expect(() => normalizeWifiFingerprint([{ bssid: 'invalid', ssid: 'Room', rssi: -55 }])).toThrow('INVALID_BSSID');
  });
});
