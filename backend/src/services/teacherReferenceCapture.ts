type TeacherReferenceCaptureInput = {
  teacherId: string;
  classroomId: string;
  lectureInstanceId: string;
  sessionId: string;
  timestamp: string;
};

export async function triggerTeacherReferenceCapture(input: TeacherReferenceCaptureInput) {
  return {
    triggered: true,
    ...input,
    status: 'PENDING_FUTURE_INTEGRATION'
  };
}
