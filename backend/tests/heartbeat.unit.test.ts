import {
  calculateRunningPresenceScore,
  normalizeWifiFingerprint,
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

  it('validates wifi fingerprint entries', () => {
    expect(() => normalizeWifiFingerprint([{ bssid: 'AA:BB:CC:DD:EE:FF', ssid: 'Room', rssi: -55 }])).not.toThrow();
    expect(() => normalizeWifiFingerprint([{ bssid: 'invalid', ssid: 'Room', rssi: -55 }])).toThrow('INVALID_BSSID');
  });
});
