jest.mock('../src/config/db', () => {
  const query = jest.fn();
  const client = { query: jest.fn(), release: jest.fn() };
  return {
    pool: {
      query,
      connect: jest.fn(async () => client)
    }
  };
});

jest.mock('../src/services/teacherReferenceCapture', () => ({
  triggerTeacherReferenceCapture: jest.fn(async (input: any) => ({
    ...input,
    accepted: true,
    provider: 'TestTeacherCaptureTrigger',
    requestId: 'capture-request-1',
    deliveryStatus: 'ACKNOWLEDGED',
    message: 'CAPTURE_REQUESTED'
  }))
}));

import { pool } from '../src/config/db';
import {
  createLectureActivationEngine,
  evaluateTodayLectures,
  getTodayLectureInstances
} from '../src/services/lectureActivationService';
import { triggerTeacherReferenceCapture } from '../src/services/teacherReferenceCapture';
import {
  BLEProvider,
  MotionCorrelationProvider,
  TeacherPresenceCoordinator,
  WifiFingerprintProvider,
  createDefaultTeacherPresenceProvider
} from '../src/services/teacherPresenceProvider';

const mockedPool = pool as unknown as { query: jest.Mock; connect: jest.Mock };
const mockedTrigger = triggerTeacherReferenceCapture as jest.MockedFunction<typeof triggerTeacherReferenceCapture>;

function deviceBindingPresentResult() {
  return {
    present: true,
    providerName: 'DeviceBinding',
    confidence: 1,
    status: 'PRESENT' as const,
    metadata: { reason: 'REGISTERED_DEVICE_BOUND' }
  };
}

function deviceBindingAbsentResult() {
  return {
    present: false,
    providerName: 'DeviceBinding',
    confidence: 0,
    status: 'NOT_PRESENT' as const,
    metadata: { reason: 'NO_ACTIVE_DEVICE_BINDING' }
  };
}

function buildClient(responses: Array<unknown>) {
  const query = jest.fn();
  for (const response of responses) {
    query.mockResolvedValueOnce(response);
  }
  return {
    query,
    release: jest.fn()
  };
}

describe('lecture activation service', () => {
  beforeEach(() => {
    mockedPool.query.mockReset();
    mockedPool.connect.mockReset();
    mockedTrigger.mockReset();
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-06-29T09:18:00.000Z'));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('reports today lecture instances for the materialized date', async () => {
    mockedPool.query.mockResolvedValueOnce({
      rowCount: 1,
      rows: [{ lectureInstanceId: 'lecture-1' }]
    });

    const result = await getTodayLectureInstances();
    expect(result.academicDate).toBe('2026-06-29');
    expect(result.lectureInstances).toHaveLength(1);
  });

  it('returns evaluation counts without mutating lecture state', async () => {
    mockedPool.query.mockResolvedValueOnce({
      rows: [
        {
          lectureinstanceid: 'lecture-1',
          timetableentryid: 'entry-1',
          lecturedate: '2026-06-29',
          scheduledstartat: '2026-06-29T09:00:00.000Z',
          scheduledendat: '2026-06-29T10:00:00.000Z',
          activationstate: 'PENDING',
          activationreason: null,
          sessionid: null,
          sessionstatus: null,
          teacherid: 'teacher-1',
          classroomid: 'class-1',
          coursename: 'Networks',
          joinwindowminutes: 5
        }
      ]
    });

    const result = await evaluateTodayLectures();
    expect(result.totalLectureInstances).toBe(1);
    expect(result.pendingCount).toBe(1);
  });

  it('activates a lecture when the teacher arrives late but still within the lecture window', async () => {
    const engine = createLectureActivationEngine({
      now: new Date('2026-06-29T09:18:00.000Z'),
      teacherPresenceProvider: {
        isTeacherPresent: jest.fn(async () => deviceBindingPresentResult())
      }
    });

    const client = buildClient([
      {},
      {
        rows: [
          {
            lectureinstanceid: 'lecture-1',
            timetableentryid: 'entry-1',
            lecturedate: '2026-06-29',
            scheduledstartat: '2026-06-29T09:00:00.000Z',
            scheduledendat: '2026-06-29T10:00:00.000Z',
            activationstate: 'PENDING',
            activationreason: null,
            sessionid: null,
            sessionstatus: null,
            teacherid: 'teacher-1',
            classroomid: 'class-1',
            coursename: 'Networks',
            joinwindowminutes: 5
          }
        ]
      },
      { rows: [{ id: 'session-1' }] },
      { rowCount: 1, rows: [{}] },
      { rowCount: 1, rows: [{}] },
      {}
    ]);
    mockedPool.connect.mockResolvedValueOnce(client as any);

    const result = await engine.run();
    expect(result.activatedCount).toBe(1);
    expect(result.activeCount).toBe(1);
    expect(mockedTrigger).toHaveBeenCalledTimes(1);
  });

  it('keeps lecture pending when the teacher is absent during the window', async () => {
    const engine = createLectureActivationEngine({
      now: new Date('2026-06-29T09:18:00.000Z'),
      teacherPresenceProvider: {
        isTeacherPresent: jest.fn(async () => deviceBindingAbsentResult())
      }
    });

    const client = buildClient([
      {},
      {
        rows: [
          {
            lectureinstanceid: 'lecture-1',
            timetableentryid: 'entry-1',
            lecturedate: '2026-06-29',
            scheduledstartat: '2026-06-29T09:00:00.000Z',
            scheduledendat: '2026-06-29T10:00:00.000Z',
            activationstate: 'PENDING',
            activationreason: null,
            sessionid: null,
            sessionstatus: null,
            teacherid: 'teacher-1',
            classroomid: 'class-1',
            coursename: 'Networks',
            joinwindowminutes: 5
          }
        ]
      }
    ]);
    mockedPool.connect.mockResolvedValueOnce(client as any);

    const result = await engine.run();
    expect(result.activatedCount).toBe(0);
    expect(result.pendingCount).toBe(1);
    expect(mockedTrigger).not.toHaveBeenCalled();
  });

  it('marks a lecture missed after its window closes without activation', async () => {
    const engine = createLectureActivationEngine({
      now: new Date('2026-06-29T10:05:00.000Z'),
      teacherPresenceProvider: {
        isTeacherPresent: jest.fn(async () => deviceBindingAbsentResult())
      }
    });

    const client = buildClient([
      {},
      {
        rows: [
          {
            lectureinstanceid: 'lecture-1',
            timetableentryid: 'entry-1',
            lecturedate: '2026-06-29',
            scheduledstartat: '2026-06-29T09:00:00.000Z',
            scheduledendat: '2026-06-29T10:00:00.000Z',
            activationstate: 'PENDING',
            activationreason: null,
            sessionid: null,
            sessionstatus: null,
            teacherid: 'teacher-1',
            classroomid: 'class-1',
            coursename: 'Networks',
            joinwindowminutes: 5
          }
        ]
      },
      { rowCount: 1, rows: [{}] }
    ]);
    mockedPool.connect.mockResolvedValueOnce(client as any);

    const result = await engine.run();
    expect(result.missedLectureCount).toBe(1);
    expect(result.missedCount).toBe(1);
  });

  it('ends an active lecture automatically at the scheduled end', async () => {
    const engine = createLectureActivationEngine({
      now: new Date('2026-06-29T10:05:00.000Z'),
      teacherPresenceProvider: {
        isTeacherPresent: jest.fn(async () => deviceBindingPresentResult())
      }
    });

    const client = buildClient([
      {},
      {
        rows: [
          {
            lectureinstanceid: 'lecture-1',
            timetableentryid: 'entry-1',
            lecturedate: '2026-06-29',
            scheduledstartat: '2026-06-29T09:00:00.000Z',
            scheduledendat: '2026-06-29T10:00:00.000Z',
            activationstate: 'ACTIVE',
            activationreason: 'TIMETABLE_AND_TEACHER_PRESENCE_MATCH',
            sessionid: 'session-1',
            sessionstatus: 'ACTIVE',
            teacherid: 'teacher-1',
            classroomid: 'class-1',
            coursename: 'Networks',
            joinwindowminutes: 5
          }
        ]
      },
      { rowCount: 1, rows: [{}] },
      {}
    ]);
    mockedPool.connect.mockResolvedValueOnce(client as any);

    const result = await engine.run();
    expect(result.endedLectureCount).toBe(1);
    expect(result.endedCount).toBe(1);
  });

  it('prevents duplicate sessions when the scheduler runs repeatedly', async () => {
    const provider = {
      isTeacherPresent: jest.fn(async () => deviceBindingPresentResult())
    };

    const firstEngine = createLectureActivationEngine({
      now: new Date('2026-06-29T09:18:00.000Z'),
      teacherPresenceProvider: provider
    });
    const secondEngine = createLectureActivationEngine({
      now: new Date('2026-06-29T09:19:00.000Z'),
      teacherPresenceProvider: provider
    });

    const firstClient = buildClient([
      {},
      {
        rows: [
          {
            lectureinstanceid: 'lecture-1',
            timetableentryid: 'entry-1',
            lecturedate: '2026-06-29',
            scheduledstartat: '2026-06-29T09:00:00.000Z',
            scheduledendat: '2026-06-29T10:00:00.000Z',
            activationstate: 'PENDING',
            activationreason: null,
            sessionid: null,
            sessionstatus: null,
            teacherid: 'teacher-1',
            classroomid: 'class-1',
            coursename: 'Networks',
            joinwindowminutes: 5
          }
        ]
      },
      { rows: [{ id: 'session-1' }] },
      { rowCount: 1, rows: [{}] },
      { rowCount: 1, rows: [{}] },
      {}
    ]);

    const secondClient = buildClient([
      {},
      {
        rows: [
          {
            lectureinstanceid: 'lecture-1',
            timetableentryid: 'entry-1',
            lecturedate: '2026-06-29',
            scheduledstartat: '2026-06-29T09:00:00.000Z',
            scheduledendat: '2026-06-29T10:00:00.000Z',
            activationstate: 'ACTIVE',
            activationreason: 'TIMETABLE_AND_TEACHER_PRESENCE_MATCH',
            sessionid: 'session-1',
            sessionstatus: 'ACTIVE',
            teacherid: 'teacher-1',
            classroomid: 'class-1',
            coursename: 'Networks',
            joinwindowminutes: 5
          }
        ]
      },
      {}
    ]);

    mockedPool.connect
      .mockResolvedValueOnce(firstClient as any)
      .mockResolvedValueOnce(secondClient as any);

    const first = await firstEngine.run();
    const second = await secondEngine.run();

    expect(first.activatedCount).toBe(1);
    expect(second.activatedCount).toBe(0);
  });

  it('returns a richer teacher-presence result from the coordinator', async () => {
    const provider = {
      isTeacherPresent: jest.fn(async () => deviceBindingPresentResult())
    };
    const engine = createLectureActivationEngine({ teacherPresenceProvider: provider });

    await expect(engine.verifyTeacherPresence('teacher-1', 'class-1', 'lecture-1')).resolves.toEqual({
      present: true,
      providerName: 'DeviceBinding',
      confidence: 1,
      status: 'PRESENT',
      metadata: { reason: 'REGISTERED_DEVICE_BOUND' }
    });
  });

  it('exposes a placeholder teacher reference capture trigger on activation', async () => {
    const provider = {
      isTeacherPresent: jest.fn(async () => deviceBindingPresentResult())
    };
    const engine = createLectureActivationEngine({
      now: new Date('2026-06-29T09:18:00.000Z'),
      teacherPresenceProvider: provider
    });

    const client = buildClient([
      {},
      {
        rows: [
          {
            lectureinstanceid: 'lecture-1',
            timetableentryid: 'entry-1',
            lecturedate: '2026-06-29',
            scheduledstartat: '2026-06-29T09:00:00.000Z',
            scheduledendat: '2026-06-29T10:00:00.000Z',
            activationstate: 'PENDING',
            activationreason: null,
            sessionid: null,
            sessionstatus: null,
            teacherid: 'teacher-1',
            classroomid: 'class-1',
            coursename: 'Networks',
            joinwindowminutes: 5
          }
        ]
      },
      { rows: [{ id: 'session-1' }] },
      { rowCount: 1, rows: [{}] },
      { rowCount: 1, rows: [{}] },
      {}
    ]);
    mockedPool.connect.mockResolvedValueOnce(client as any);

    await engine.run();
    expect(mockedTrigger).toHaveBeenCalledTimes(1);
  });

  it('aggregates multiple providers without changing activation semantics', async () => {
    const coordinator = new TeacherPresenceCoordinator([
      {
        providerName: 'DeviceBinding',
        checkTeacherPresence: jest.fn(async () => ({
          present: true,
          providerName: 'DeviceBinding',
          confidence: 1,
          status: 'PRESENT' as const,
          metadata: { reason: 'REGISTERED_DEVICE_BOUND' }
        }))
      },
      {
        providerName: 'WifiFingerprint',
        checkTeacherPresence: jest.fn(async () => ({
          present: false,
          providerName: 'WifiFingerprint',
          confidence: 0,
          status: 'NOT_IMPLEMENTED' as const,
          metadata: { reason: 'FUTURE_WIFI_FINGERPRINT_PROVIDER' }
        }))
      }
    ]);

    const result = await coordinator.isTeacherPresent('teacher-1', 'class-1', 'lecture-1', new Date('2026-06-29T09:18:00.000Z'));

    expect(result.present).toBe(true);
    expect(result.providerName).toBe('DeviceBinding');
    expect(result.confidence).toBe(1);
    expect(result.metadata).toHaveProperty('providerResults');
    expect(Array.isArray(result.metadata.providerResults)).toBe(true);
    expect(result.metadata.providerResults).toHaveLength(2);
  });

  it('returns placeholder results from stub providers', async () => {
    const wifiStub = new WifiFingerprintProvider();
    const bleStub = new BLEProvider();
    const motionStub = new MotionCorrelationProvider();

    await expect(wifiStub.checkTeacherPresence('teacher-1', 'class-1', 'lecture-1', new Date())).resolves.toMatchObject({
      present: false,
      providerName: 'WifiFingerprint',
      status: 'NOT_IMPLEMENTED'
    });

    await expect(bleStub.checkTeacherPresence('teacher-1', 'class-1', 'lecture-1', new Date())).resolves.toMatchObject({
      present: false,
      providerName: 'BLE',
      status: 'NOT_IMPLEMENTED'
    });

    await expect(motionStub.checkTeacherPresence('teacher-1', 'class-1', 'lecture-1', new Date())).resolves.toMatchObject({
      present: false,
      providerName: 'MotionCorrelation',
      status: 'FUTURE_RESEARCH'
    });
  });

  it('supports provider registration on the coordinator', async () => {
    const coordinator = createDefaultTeacherPresenceProvider();
    const customProvider = {
      providerName: 'CustomProvider',
      checkTeacherPresence: jest.fn(async () => ({
        present: false,
        providerName: 'CustomProvider',
        confidence: 0,
        status: 'NOT_IMPLEMENTED' as const,
        metadata: { reason: 'CUSTOM_PLACEHOLDER' }
      }))
    };

    mockedPool.query.mockResolvedValueOnce({
      rowCount: 1,
      rows: [{ id: 'binding-1' }]
    });
    coordinator.registerProvider(customProvider);
    const result = await coordinator.isTeacherPresent('teacher-1', 'class-1', 'lecture-1', new Date('2026-06-29T09:18:00.000Z'));

    expect(result.metadata).toHaveProperty('providerResults');
    expect((result.metadata.providerResults as unknown[]).length).toBeGreaterThanOrEqual(5);
    expect(result.present).toBe(true);
    expect(result.providerName).toBe('DeviceBinding');
  });
});