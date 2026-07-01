import { validateRegisteredDevice } from '../auth/deviceService';

export type TeacherPresenceStatus = 'PRESENT' | 'NOT_PRESENT' | 'NOT_IMPLEMENTED' | 'FUTURE_RESEARCH';

export type TeacherPresenceResult = {
  present: boolean;
  confidence: number;
  providerName: string;
  status: TeacherPresenceStatus;
  metadata: Record<string, unknown>;
};

export interface TeacherPresenceCheckProvider {
  providerName: string;
  checkTeacherPresence(
    teacherId: string,
    classroomId: string,
    lectureInstanceId: string,
    timestamp: Date
  ): Promise<TeacherPresenceResult>;
}

export interface TeacherPresenceProvider {
  registerProvider?(provider: TeacherPresenceCheckProvider): void;
  isTeacherPresent(
    teacherId: string,
    classroomId: string,
    lectureInstanceId: string,
    timestamp: Date
  ): Promise<TeacherPresenceResult>;
}

function createPlaceholderResult(
  providerName: string,
  status: TeacherPresenceStatus,
  metadata: Record<string, unknown> = {}
): TeacherPresenceResult {
  return {
    present: false,
    confidence: 0,
    providerName,
    status,
    metadata
  };
}

class DeviceBindingProvider implements TeacherPresenceCheckProvider {
  providerName = 'DeviceBinding';

  async checkTeacherPresence(
    teacherId: string,
    _classroomId: string,
    _lectureInstanceId: string,
    _timestamp: Date
  ): Promise<TeacherPresenceResult> {
    const binding = await validateRegisteredDevice(teacherId);
    if (!binding) {
      return createPlaceholderResult(this.providerName, 'NOT_PRESENT', {
        reason: 'NO_ACTIVE_DEVICE_BINDING'
      });
    }

    return {
      present: true,
      confidence: 1.0,
      providerName: this.providerName,
      status: 'PRESENT',
      metadata: {
        bindingId: binding.id,
        reason: 'REGISTERED_DEVICE_BOUND'
      }
    };
  }
}

export class WifiFingerprintProvider implements TeacherPresenceCheckProvider {
  providerName = 'WifiFingerprint';

  async checkTeacherPresence(
    _teacherId: string,
    _classroomId: string,
    _lectureInstanceId: string,
    _timestamp: Date
  ): Promise<TeacherPresenceResult> {
    return createPlaceholderResult(this.providerName, 'NOT_IMPLEMENTED', {
      reason: 'FUTURE_WIFI_FINGERPRINT_PROVIDER'
    });
  }
}

export class BLEProvider implements TeacherPresenceCheckProvider {
  providerName = 'BLE';

  async checkTeacherPresence(
    _teacherId: string,
    _classroomId: string,
    _lectureInstanceId: string,
    _timestamp: Date
  ): Promise<TeacherPresenceResult> {
    return createPlaceholderResult(this.providerName, 'NOT_IMPLEMENTED', {
      reason: 'FUTURE_BLE_PROVIDER'
    });
  }
}

export class MotionCorrelationProvider implements TeacherPresenceCheckProvider {
  providerName = 'MotionCorrelation';

  async checkTeacherPresence(
    _teacherId: string,
    _classroomId: string,
    _lectureInstanceId: string,
    _timestamp: Date
  ): Promise<TeacherPresenceResult> {
    return createPlaceholderResult(this.providerName, 'FUTURE_RESEARCH', {
      reason: 'FUTURE_RESEARCH_PROVIDER'
    });
  }
}

function aggregateTeacherPresenceResults(results: TeacherPresenceResult[]): TeacherPresenceResult {
  const confirmed = results.find((result) => result.present);
  if (confirmed) {
    return {
      ...confirmed,
      metadata: {
        ...confirmed.metadata,
        providerResults: results
      }
    };
  }

  const observed = results[0];
  if (!observed) {
    return createPlaceholderResult('None', 'NOT_PRESENT', { providerResults: [] });
  }

  return {
    ...observed,
    present: false,
    confidence: 0,
    providerName: observed.providerName,
    status: observed.status === 'NOT_IMPLEMENTED' ? 'NOT_IMPLEMENTED' : observed.status,
    metadata: {
      ...observed.metadata,
      providerResults: results
    }
  };
}

class TeacherPresenceCoordinator implements TeacherPresenceProvider {
  private readonly providers: TeacherPresenceCheckProvider[];

  constructor(providers: TeacherPresenceCheckProvider[] = [new DeviceBindingProvider(), new WifiFingerprintProvider(), new BLEProvider(), new MotionCorrelationProvider()]) {
    this.providers = [...providers];
  }

  registerProvider(provider: TeacherPresenceCheckProvider) {
    this.providers.push(provider);
  }

  async isTeacherPresent(
    teacherId: string,
    classroomId: string,
    lectureInstanceId: string,
    timestamp: Date
  ): Promise<TeacherPresenceResult> {
    const results: TeacherPresenceResult[] = [];
    for (const provider of this.providers) {
      const result = await provider.checkTeacherPresence(teacherId, classroomId, lectureInstanceId, timestamp);
      results.push(result);
    }

    return aggregateTeacherPresenceResults(results);
  }
}

export function createDefaultTeacherPresenceProvider() {
  return new TeacherPresenceCoordinator();
}

export {
  DeviceBindingProvider,
  TeacherPresenceCoordinator,
  aggregateTeacherPresenceResults
};
