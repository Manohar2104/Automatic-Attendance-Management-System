import { createPlaceholderTeacherReferenceCaptureTrigger, triggerTeacherReferenceCapture } from '../src/services/teacherReferenceCapture';

describe('teacher reference capture trigger', () => {
  it('returns a rich capture result with accepted metadata', async () => {
    const result = await triggerTeacherReferenceCapture({
      teacherId: 'teacher-1',
      classroomId: 'class-1',
      lectureInstanceId: 'lecture-1',
      sessionId: 'session-1',
      timestamp: '2026-06-29T09:18:00.000Z'
    });

    expect(result.accepted).toBe(true);
    expect(result.provider).toBe('PlaceholderTeacherCaptureTrigger');
    expect(result.deliveryStatus).toBe('ACKNOWLEDGED');
    expect(result.message).toBe('CAPTURE_REQUESTED');
    expect(result.requestId).toEqual(expect.any(String));
  });

  it('exposes the placeholder coordinator contract for future transports', async () => {
    const trigger = createPlaceholderTeacherReferenceCaptureTrigger();
    const result = await trigger.requestTeacherReferenceCapture({
      teacherId: 'teacher-1',
      classroomId: 'class-1',
      lectureInstanceId: 'lecture-1',
      sessionId: 'session-1',
      timestamp: '2026-06-29T09:18:00.000Z'
    });

    expect(result.accepted).toBe(true);
    expect(result.requestId).toEqual(expect.any(String));
  });
});