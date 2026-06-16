import { endSession } from '../src/services/sessionService';

jest.mock('../src/config/db', () => {
  const client = { query: jest.fn(), release: jest.fn() };
  return { pool: { connect: jest.fn(async () => client), query: jest.fn() } };
});

import { pool } from '../src/config/db';

describe('session closure finalization integration', () => {
  const mockedPool = pool as unknown as { connect: jest.Mock; query: jest.Mock };

  beforeEach(() => {
    mockedPool.connect.mockReset();
  });

  it('runs finalization when session is ended', async () => {
    const client1 = {
      query: jest.fn()
        // BEGIN
        .mockResolvedValueOnce({})
        // SELECT session FOR UPDATE
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', status: 'ACTIVE' }] })
        // UPDATE sessions RETURNING *
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', status: 'CLOSED' }] })
        // COMMIT
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    const client2 = {
      query: jest.fn()
        // BEGIN
        .mockResolvedValueOnce({})
        // SELECT thresholds
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ presence_threshold_present: 85, presence_threshold_partial: 60 }] })
        // SELECT attendance rows
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'a-1', student_id: 's-1', status: 'PRESENT', join_score: 100, confidence_score: 90, confidence_breakdown: null }] })
        // UPDATE attendance
        .mockResolvedValueOnce({})
        // COMMIT
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client1 as any).mockResolvedValueOnce(client2 as any);

    const result = await endSession('session-1');
    expect(result.status).toBe('CLOSED');
    expect(result.finalization).toBeDefined();
    expect(result.finalization.finalized >= 0).toBeTruthy();
    expect(client2.query.mock.calls.some(([sql]) => String(sql).includes('final_score'))).toBe(true);
  });

  it('applies session-specific thresholds during finalization', async () => {
    const client1 = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-2', status: 'ACTIVE' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-2', status: 'CLOSED' }] })
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    const client2 = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ presence_threshold_present: 90, presence_threshold_partial: 70 }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'a-2', student_id: 's-2', status: 'PARTIAL', join_score: 50, confidence_score: 80, confidence_breakdown: null, finalized_at: null }] })
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(client1 as any).mockResolvedValueOnce(client2 as any);

    const result = await endSession('session-2');
    expect(result.finalization.finalized).toBe(1);
    expect(result.finalization.details[0].finalScore).toBe(74);
    expect(result.finalization.details[0].newStatus).toBe('PARTIAL');
  });
});
