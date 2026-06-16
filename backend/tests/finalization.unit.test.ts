import { classifyFinalAttendanceStatus, computeFinalAttendanceScore, finalizeAttendanceForSession } from '../src/services/attendanceFinalizationService';

jest.mock('../src/config/db', () => {
  const client = { query: jest.fn(), release: jest.fn() };
  return { pool: { connect: jest.fn(async () => client), query: jest.fn() } };
});

import { pool } from '../src/config/db';

describe('attendance finalization service', () => {
  const mockedPool = pool as unknown as { connect: jest.Mock; query: jest.Mock };

  beforeEach(() => {
    mockedPool.connect.mockReset();
  });

  it('calculates the weighted final score using the approved 80/20 formula', () => {
    expect(computeFinalAttendanceScore(100, 50)).toBe(90);
    expect(computeFinalAttendanceScore(87, 60)).toBe(82);
    expect(computeFinalAttendanceScore(0, 0)).toBe(0);
  });

  it('classifies PRESENT at the present threshold boundary', () => {
    expect(classifyFinalAttendanceStatus(85, 85, 60)).toBe('PRESENT');
  });

  it('classifies PARTIAL at the partial threshold boundary', () => {
    expect(classifyFinalAttendanceStatus(60, 85, 60)).toBe('PARTIAL');
  });

  it('classifies ABSENT below the partial threshold boundary', () => {
    expect(classifyFinalAttendanceStatus(59, 85, 60)).toBe('ABSENT');
  });

  it('classifies PRESENT when weighted score meets present threshold', async () => {
    const client = {
      query: jest.fn()
        // BEGIN
        .mockResolvedValueOnce({})
        // session thresholds (no custom thresholds)
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ presence_threshold_present: null, presence_threshold_partial: null }] })
        // attendance rows
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'a-1', student_id: 's-1', status: 'PRESENT', join_score: 100, confidence_score: 93, confidence_breakdown: null, finalized_at: null }] })
        // update attendance
        .mockResolvedValueOnce({})
        // COMMIT
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);

    const result = await finalizeAttendanceForSession('session-1');
    expect(result.finalized).toBe(1);
    expect(result.skippedRejected).toBe(0);
    expect(result.details[0].studentId).toBe('s-1');
    expect(result.details[0].finalScore).toBe(94);
    expect(result.details[0].newStatus).toBe('PRESENT');
  });

  it('classifies PARTIAL when weighted score stays between the thresholds', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ presence_threshold_present: 85, presence_threshold_partial: 60 }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'a-2', student_id: 's-2', status: 'PARTIAL', join_score: 50, confidence_score: 70, confidence_breakdown: null, finalized_at: null }] })
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);

    const result = await finalizeAttendanceForSession('session-1');
    expect(result.finalized).toBe(1);
    expect(result.details[0].finalScore).toBe(66);
    expect(result.details[0].newStatus).toBe('PARTIAL');
  });

  it('classifies ABSENT when weighted score stays below the partial threshold', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ presence_threshold_present: 85, presence_threshold_partial: 60 }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'a-2', student_id: 's-2', status: 'PARTIAL', join_score: 0, confidence_score: 30, confidence_breakdown: null, finalized_at: null }] })
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);

    const result = await finalizeAttendanceForSession('session-1');
    expect(result.finalized).toBe(1);
    expect(result.details[0].finalScore).toBe(24);
    expect(result.details[0].newStatus).toBe('ABSENT');
  });

  it('preserves REJECTED attendance status', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ presence_threshold_present: 85, presence_threshold_partial: 60 }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'a-3', student_id: 's-3', status: 'REJECTED', join_score: 0, confidence_score: null, confidence_breakdown: null, finalized_at: null }] })
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);

    const result = await finalizeAttendanceForSession('session-1');
    expect(result.finalized).toBe(0);
    expect(result.skippedRejected).toBe(1);
    expect(result.details[0].previousStatus).toBe('REJECTED');
  });

  it('preserves already finalized attendance rows without updating them again', async () => {
    const client = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ presence_threshold_present: 85, presence_threshold_partial: 60 }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'a-4', student_id: 's-4', status: 'PRESENT', join_score: 100, confidence_score: 100, confidence_breakdown: null, finalized_at: '2026-06-16T10:00:00.000Z' }] })
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client as any);

    const result = await finalizeAttendanceForSession('session-1');
    expect(result.finalized).toBe(0);
    expect(result.details[0].newStatus).toBe('PRESENT');
  });
});
