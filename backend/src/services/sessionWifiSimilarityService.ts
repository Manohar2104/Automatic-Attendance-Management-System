import {
  loadReferenceFingerprintForSession,
  type ReferenceFingerprintLoadResult
} from './referenceFingerprintLoader';
import {
  loadLatestStudentFingerprintForSession,
  type StudentFingerprintLoadResult
} from './studentFingerprintLoader';
import {
  calculateSimilarity,
  type SimilarityResult
} from './wifiSimilarityEngine';

export type SessionWifiSimilarityResult = SimilarityResult & {
  sessionId: string;
  studentId: string;
  referenceStatus: ReferenceFingerprintLoadResult['status'];
  studentFingerprintStatus: StudentFingerprintLoadResult['status'];
};

export async function compareStudentToTeacherReference(
  sessionId: string,
  studentId: string
): Promise<SessionWifiSimilarityResult> {
  const comparisonTimestamp = new Date().toISOString();

  const [reference, student] = await Promise.all([
    loadReferenceFingerprintForSession(sessionId),
    loadLatestStudentFingerprintForSession(sessionId, studentId)
  ]);

  const result = calculateSimilarity(reference.fingerprintData, student.fingerprintData, {
    provider: 'WifiReferenceCollector',
    teacherFingerprintTimestamp: reference.capturedAt,
    studentFingerprintTimestamp: student.heartbeatTimestamp,
    comparisonTimestamp
  });

  return {
    ...result,
    sessionId,
    studentId,
    referenceStatus: reference.status,
    studentFingerprintStatus: student.status
  };
}
