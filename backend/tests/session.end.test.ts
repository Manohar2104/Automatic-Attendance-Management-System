jest.mock('../src/config/db', () => {
  const client = { query: jest.fn(), release: jest.fn() };
  return { pool: { connect: jest.fn(async () => client), query: jest.fn() } };
});

jest.mock('../src/services/attendanceFinalizationService', () => ({
  finalizeAttendanceForSession: jest.fn(async (sessionId: string) => ({
    sessionId,
    finalized: 1,
    skippedRejected: 0,
    details: []
  }))
}));

import { pool } from '../src/config/db';
import { finalizeAttendanceForSession } from '../src/services/attendanceFinalizationService';
import { endSession } from '../src/services/sessionService';

describe('session end idempotence', () => {
  const mockedPool = pool as unknown as { connect: jest.Mock; query: jest.Mock };
  const mockedFinalize = finalizeAttendanceForSession as unknown as jest.Mock;

  beforeEach(() => {
    mockedPool.connect.mockReset();
    mockedFinalize.mockClear();
  });

  it('finalizes once for an active session and does not refinalize on repeated closure attempts', async () => {
    const activeClient = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', status: 'ACTIVE' }] })
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', status: 'CLOSED' }] })
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    const closedClient = {
      query: jest.fn()
        .mockResolvedValueOnce({})
        .mockResolvedValueOnce({ rowCount: 1, rows: [{ id: 'session-1', status: 'CLOSED' }] })
        .mockResolvedValueOnce({}),
      release: jest.fn()
    };

    mockedPool.connect.mockResolvedValueOnce(activeClient as any).mockResolvedValueOnce(closedClient as any);

    const first = await endSession('session-1');
    expect(first.status).toBe('CLOSED');
    expect(mockedFinalize).toHaveBeenCalledTimes(1);

    await expect(endSession('session-1')).rejects.toMatchObject({ message: 'SESSION_NOT_ACTIVE' });
    expect(mockedFinalize).toHaveBeenCalledTimes(1);
  });
});
