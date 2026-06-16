import { computeJoinOutcome, shouldRejectHeartbeatForAttendanceStatus, validatePresenceThresholds } from '../src/services/sessionService';

describe('session service utilities', () => {
  it('assigns joinScore 100 for joins within 0-2 minutes', () => {
    const start = new Date('2026-06-15T10:00:00.000Z');
    const outcome = computeJoinOutcome(start, new Date('2026-06-15T10:02:00.000Z'));
    expect(outcome.joinScore).toBe(100);
    expect(outcome.attendanceStatus).toBe('PRESENT');
  });

  it('assigns joinScore 50 for joins after 2 minutes and up to 5 minutes', () => {
    const start = new Date('2026-06-15T10:00:00.000Z');
    const outcome = computeJoinOutcome(start, new Date('2026-06-15T10:04:00.000Z'));
    expect(outcome.joinScore).toBe(50);
    expect(outcome.attendanceStatus).toBe('PARTIAL');
  });

  it('assigns REJECTED after 5 minutes', () => {
    const start = new Date('2026-06-15T10:00:00.000Z');
    const outcome = computeJoinOutcome(start, new Date('2026-06-15T10:05:01.000Z'));
    expect(outcome.joinScore).toBe(0);
    expect(outcome.attendanceStatus).toBe('REJECTED');
  });

  it('accepts valid thresholds and rejects conflicts', () => {
    expect(validatePresenceThresholds({ presenceThresholdPresent: 90, presenceThresholdPartial: 60 })).toEqual({
      presenceThresholdPresent: 90,
      presenceThresholdPartial: 60
    });
    expect(() => validatePresenceThresholds({ presenceThresholdPresent: 80, presenceThresholdPartial: 80 })).toThrow('THRESHOLD_CONFLICT');
  });

  it('rejects heartbeats for REJECTED attendance status', () => {
    expect(shouldRejectHeartbeatForAttendanceStatus('REJECTED')).toBe(true);
    expect(shouldRejectHeartbeatForAttendanceStatus('PRESENT')).toBe(false);
  });
});
