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
  captureDelivered(result: TeacherReferenceCaptureResult): void;
  captureAcknowledged(result: TeacherReferenceCaptureResult): void;
  captureFailed(result: TeacherReferenceCaptureResult, error?: unknown): void;
  captureExpired(result: TeacherReferenceCaptureResult): void;
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
    captureDelivered(result: TeacherReferenceCaptureResult) {
      console.info('teacher_capture_delivered', result.requestId, result.sessionId);
    },
    captureAcknowledged(result: TeacherReferenceCaptureResult) {
      console.info('teacher_capture_acknowledged', result.requestId, result.sessionId);
    },
    captureFailed(result: TeacherReferenceCaptureResult, error?: unknown) {
      console.warn('teacher_capture_failed', result.requestId, result.sessionId, error);
    },
    captureExpired(result: TeacherReferenceCaptureResult) {
      console.warn('teacher_capture_expired', result.requestId, result.sessionId);
    }
  };
}

export function createPlaceholderTeacherReferenceCaptureTrigger(): TeacherReferenceCaptureTrigger {
  const audit = createLoggingTeacherCaptureAudit();
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

      audit.onRequested(result);
      lifecycle.captureRequested(result);
      audit.onDelivered(result);
      lifecycle.captureDelivered(result);
      audit.onAcknowledged({
        ...result,
        deliveryStatus: 'ACKNOWLEDGED'
      });
      lifecycle.captureAcknowledged({
        ...result,
        deliveryStatus: 'ACKNOWLEDGED'
      });

      return {
        ...result,
        deliveryStatus: 'ACKNOWLEDGED'
      };
    }
  };
}

const placeholderTeacherReferenceCaptureTrigger = createPlaceholderTeacherReferenceCaptureTrigger();

export async function triggerTeacherReferenceCapture(input: TeacherReferenceCaptureInput) {
  return placeholderTeacherReferenceCaptureTrigger.requestTeacherReferenceCapture(input);
}
