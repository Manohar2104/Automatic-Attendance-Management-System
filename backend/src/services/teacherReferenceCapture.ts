import { randomUUID } from 'crypto';

type TeacherReferenceCaptureInput = {
  teacherId: string;
  classroomId: string;
  lectureInstanceId: string;
  sessionId: string;
  timestamp: string;
};

export type TeacherReferenceCaptureResult = TeacherReferenceCaptureInput & {
  accepted: boolean;
  provider: string;
  requestId: string;
  deliveryStatus: 'REQUESTED' | 'DELIVERED' | 'ACKNOWLEDGED' | 'FAILED';
  message: string;
};

export type TeacherCaptureResult = TeacherReferenceCaptureResult;

export interface TeacherReferenceCaptureTrigger {
  requestTeacherReferenceCapture(input: TeacherReferenceCaptureInput): Promise<TeacherReferenceCaptureResult>;
}

export interface TeacherCaptureAudit {
  onRequested(result: TeacherReferenceCaptureResult): void;
  onDelivered(result: TeacherReferenceCaptureResult): void;
  onAcknowledged(result: TeacherReferenceCaptureResult): void;
  onFailed(result: TeacherReferenceCaptureResult, error?: unknown): void;
}

export interface TeacherCaptureLifecycle {
  captureRequested(result: TeacherReferenceCaptureResult): void;
  captureStarted(result: TeacherReferenceCaptureResult): void;
  captureCompleted(result: TeacherReferenceCaptureResult): void;
  captureFailed(result: TeacherReferenceCaptureResult, error?: unknown): void;
}

export function createLoggingTeacherCaptureAudit(): TeacherCaptureAudit {
  return {
    onRequested(result: TeacherReferenceCaptureResult) {
      console.info('teacher_capture_requested', result.requestId, result.sessionId);
    },
    onDelivered(result: TeacherReferenceCaptureResult) {
      console.info('teacher_capture_delivered', result.requestId, result.sessionId);
    },
    onAcknowledged(result: TeacherReferenceCaptureResult) {
      console.info('teacher_capture_acknowledged', result.requestId, result.sessionId);
    },
    onFailed(result: TeacherReferenceCaptureResult, error?: unknown) {
      console.warn('teacher_capture_failed', result.requestId, result.sessionId, error);
    }
  };
}

export function createLoggingTeacherCaptureLifecycle(): TeacherCaptureLifecycle {
  return {
    captureRequested(result: TeacherReferenceCaptureResult) {
      console.info('teacher_capture_requested', result.requestId, result.sessionId);
    },
    captureStarted(result: TeacherReferenceCaptureResult) {
      console.info('teacher_capture_started', result.requestId, result.sessionId);
    },
    captureCompleted(result: TeacherReferenceCaptureResult) {
      console.info('teacher_capture_completed', result.requestId, result.sessionId);
    },
    captureFailed(result: TeacherReferenceCaptureResult, error?: unknown) {
      console.warn('teacher_capture_failed', result.requestId, result.sessionId, error);
    }
  };
}

export function createPlaceholderTeacherReferenceCaptureTrigger(): TeacherReferenceCaptureTrigger {
  const lifecycle = createLoggingTeacherCaptureLifecycle();

  return {
    async requestTeacherReferenceCapture(input: TeacherReferenceCaptureInput): Promise<TeacherReferenceCaptureResult> {
      const result: TeacherReferenceCaptureResult = {
        ...input,
        accepted: true,
        provider: 'PlaceholderTeacherCaptureTrigger',
        requestId: randomUUID(),
        deliveryStatus: 'REQUESTED',
        message: 'CAPTURE_REQUESTED'
      };

      try {
        lifecycle.captureRequested(result);
        lifecycle.captureStarted(result);

        const completedResult: TeacherReferenceCaptureResult = {
          ...result,
          deliveryStatus: 'ACKNOWLEDGED'
        };

        lifecycle.captureCompleted(completedResult);

        return completedResult;
      } catch (error) {
        lifecycle.captureFailed(result, error);
        throw error;
      }
    }
  };
}

const placeholderTeacherReferenceCaptureTrigger = createPlaceholderTeacherReferenceCaptureTrigger();

export async function triggerTeacherReferenceCapture(input: TeacherReferenceCaptureInput) {
  return placeholderTeacherReferenceCaptureTrigger.requestTeacherReferenceCapture(input);
}
