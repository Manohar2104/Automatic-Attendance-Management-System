import {
  AttendanceDecisionReason,
  ConfidenceBreakdown,
  ConfidenceDecision,
  ConfidenceEngine,
  CONFIDENCE_ENGINE_VERSION,
  ConfidenceEvaluationContext,
  ConfidenceEvaluationMetadata,
  ConfidenceEvaluationRequest,
  ConfidenceEvaluationResponse,
  ConfidenceFactor,
  ConfidenceResult,
  ConfidenceStatus,
  DecisionType,
  EligibilityStatus,
  FactorContribution,
  FactorType,
  createAttendanceEligibility,
  createConfidenceBreakdown,
  createConfidenceDecision,
  createConfidenceEvaluationContext,
  createConfidenceEvaluationMetadata,
  createConfidenceEvaluationRequest,
  createConfidenceEvaluationResponse,
  createConfidenceFactor,
  createConfidenceResult
} from '../src/models/confidence';

describe('confidence models', () => {
  it('exposes the expected enum values', () => {
    expect(Object.values(ConfidenceStatus)).toEqual(expect.arrayContaining(['PENDING', 'VALID', 'DEGRADED', 'FAILED', 'UNKNOWN']));
    expect(Object.values(DecisionType)).toEqual(expect.arrayContaining(['UNDECIDED', 'PRESENT', 'PARTIAL', 'ABSENT', 'REVIEW']));
    expect(Object.values(AttendanceDecisionReason)).toEqual(expect.arrayContaining(['DEVICE_BINDING_CONFIRMED', 'TEACHER_PRESENCE_CONFIRMED', 'WIFI_SIMILARITY_HIGH', 'FUTURE_BLE_SIGNAL', 'FUTURE_MOTION_SIGNAL']));
    expect(Object.values(FactorType)).toEqual(expect.arrayContaining(['DEVICE_BINDING', 'TEACHER_PRESENCE', 'WIFI_SIMILARITY', 'FUTURE_BLE', 'FUTURE_MOTION_CORRELATION']));
    expect(Object.values(EligibilityStatus)).toEqual(expect.arrayContaining(['UNKNOWN', 'ELIGIBLE', 'INELIGIBLE', 'PENDING']));
  });

  it('builds immutable factor, breakdown, and result models', () => {
    const metadata = createConfidenceEvaluationMetadata({
      evaluationId: 'evaluation-1',
      version: CONFIDENCE_ENGINE_VERSION,
      source: 'unit-test',
      createdAt: '2026-07-01T10:00:00.000Z',
      tags: ['phase-4.4'],
      details: { context: 'test-context', notes: ['origin'], tags: ['factor-meta'] }
    });

    const factor: ConfidenceFactor = createConfidenceFactor({
      type: FactorType.WIFI_SIMILARITY,
      name: 'Wi-Fi Similarity',
      status: ConfidenceStatus.VALID,
      score: null,
      details: { signal: 'NEAR_CLASSROOM', notes: ['classification'], tags: ['signal'] },
      timestamp: '2026-07-01T10:00:01.000Z',
      metadata: {
        evaluationId: 'factor-evaluation-1',
        version: CONFIDENCE_ENGINE_VERSION,
        source: 'unit-test-factor',
        createdAt: '2026-07-01T10:00:00.000Z',
        tags: ['factor'],
        details: { signal: 'wifi', notes: ['factor-note'], tags: ['factor-tag'] }
      }
    });

    const eligibility = createAttendanceEligibility({
      status: EligibilityStatus.ELIGIBLE,
      reason: AttendanceDecisionReason.DAILY_REGISTRATION_VALID,
      details: { rationale: 'registered', notes: ['eligibility'], tags: ['eligibility'] },
      timestamp: '2026-07-01T10:00:02.000Z',
      metadata: {
        evaluationId: 'eligibility-evaluation-1',
        version: CONFIDENCE_ENGINE_VERSION,
        source: 'unit-test-eligibility',
        createdAt: '2026-07-01T10:00:00.000Z',
        tags: ['eligibility'],
        details: { rationale: 'eligible', notes: ['eligibility-note'], tags: ['eligibility-tag'] }
      }
    });

    const decision: ConfidenceDecision = createConfidenceDecision({
      type: DecisionType.REVIEW,
      reason: AttendanceDecisionReason.TEACHER_PRESENCE_CONFIRMED,
      eligibility,
      details: { rationale: 'review', notes: ['decision'], tags: ['decision'] },
      timestamp: '2026-07-01T10:00:03.000Z',
      metadata: {
        evaluationId: 'decision-evaluation-1',
        version: CONFIDENCE_ENGINE_VERSION,
        source: 'unit-test-decision',
        createdAt: '2026-07-01T10:00:00.000Z',
        tags: ['decision'],
        details: { rationale: 'review', notes: ['decision-note'], tags: ['decision-tag'] }
      }
    });

    const breakdown: ConfidenceBreakdown = createConfidenceBreakdown({
      summary: {
        factorCount: 1,
        evaluatedFactorCount: 1,
        availableFactorCount: 1,
        missingFactorCount: 0,
        decisionType: DecisionType.REVIEW,
        eligibilityStatus: EligibilityStatus.ELIGIBLE,
        confidenceStatus: ConfidenceStatus.VALID,
        reason: AttendanceDecisionReason.TEACHER_PRESENCE_CONFIRMED,
        evaluationTimestamp: '2026-07-01T10:00:04.000Z'
      },
      factors: [factor],
      notes: ['placeholder'],
      metadata: {
        evaluationId: 'breakdown-evaluation-1',
        version: CONFIDENCE_ENGINE_VERSION,
        source: 'unit-test-breakdown',
        createdAt: '2026-07-01T10:00:00.000Z',
        tags: ['breakdown'],
        details: { context: 'breakdown', notes: ['breakdown-note'], tags: ['breakdown-tag'] }
      }
    });

    const result: ConfidenceResult = createConfidenceResult({
      overallConfidence: null,
      decision,
      eligibility,
      breakdown,
      warnings: ['pending-confidence-calculation'],
      metadata: {
        evaluationId: 'result-evaluation-1',
        version: CONFIDENCE_ENGINE_VERSION,
        source: 'unit-test-result',
        createdAt: '2026-07-01T10:00:00.000Z',
        tags: ['result'],
        details: { context: 'result', notes: ['result-note'], tags: ['result-tag'] }
      },
      evaluationTimestamp: '2026-07-01T10:00:04.000Z',
      algorithmVersion: CONFIDENCE_ENGINE_VERSION,
      confidenceFactors: [factor],
      status: ConfidenceStatus.PENDING
    });

    expect(Object.isFrozen(metadata)).toBe(true);
    expect(Object.isFrozen(factor)).toBe(true);
    expect(Object.isFrozen(breakdown)).toBe(true);
    expect(Object.isFrozen(result)).toBe(true);
    expect(Object.isFrozen(result.confidenceFactors)).toBe(true);
    expect(Object.isFrozen(result.breakdown.factors)).toBe(true);
    expect(result.overallConfidence).toBeNull();
    expect(result.decision.type).toBe(DecisionType.REVIEW);
    expect(result.eligibility.status).toBe(EligibilityStatus.ELIGIBLE);
    expect(result.confidenceFactors[0].type).toBe(FactorType.WIFI_SIMILARITY);
    expect(result.metadata.evaluationId).toBe('result-evaluation-1');
    expect(result.algorithmVersion).toBe(CONFIDENCE_ENGINE_VERSION);
    expect(result.breakdown.summary.decisionType).toBe(DecisionType.REVIEW);
  });

  it('serializes and reconstructs safely through JSON', () => {
    const factor = createConfidenceFactor({
      type: FactorType.TOKEN_VALIDATION,
      name: 'Token Validation',
      details: { signal: 'accepted', notes: ['token'], tags: ['token'] },
      metadata: {
        evaluationId: 'token-factor-evaluation',
        version: CONFIDENCE_ENGINE_VERSION,
        source: 'unit-test-token-factor',
        createdAt: '2026-07-01T10:00:00.000Z',
        tags: ['token-factor'],
        details: { signal: 'token', notes: ['token-note'], tags: ['token-tag'] }
      }
    });

    const result = createConfidenceResult({
      confidenceFactors: [factor],
      warnings: ['token pending'],
      metadata: createConfidenceEvaluationMetadata({
        evaluationId: 'json-eval',
        version: CONFIDENCE_ENGINE_VERSION,
        source: 'json-test',
        details: { context: 'json', notes: ['json-note'], tags: ['json-tag'] }
      }),
      decision: createConfidenceDecision({
        type: DecisionType.UNDECIDED,
        reason: AttendanceDecisionReason.UNKNOWN,
        eligibility: createAttendanceEligibility({
          status: EligibilityStatus.UNKNOWN,
          reason: AttendanceDecisionReason.UNKNOWN,
          metadata: {
            evaluationId: 'json-eligibility-evaluation',
            version: CONFIDENCE_ENGINE_VERSION,
            source: 'json-eligibility',
            details: { rationale: 'unknown', notes: ['json-eligibility-note'], tags: ['json-eligibility-tag'] }
          }
        }),
        details: { rationale: 'json-decision', notes: ['json-decision-note'], tags: ['json-decision-tag'] },
        metadata: {
          evaluationId: 'json-decision-evaluation',
          version: CONFIDENCE_ENGINE_VERSION,
          source: 'json-decision',
          details: { rationale: 'json-decision', notes: ['json-decision-note'], tags: ['json-decision-tag'] }
        }
      }),
      eligibility: createAttendanceEligibility({
        status: EligibilityStatus.UNKNOWN,
        reason: AttendanceDecisionReason.UNKNOWN,
        metadata: {
          evaluationId: 'json-eligibility-evaluation-2',
          version: CONFIDENCE_ENGINE_VERSION,
          source: 'json-eligibility-2',
          details: { rationale: 'unknown', notes: ['json-eligibility-note-2'], tags: ['json-eligibility-tag-2'] }
        }
      }),
      breakdown: createConfidenceBreakdown({
        summary: {
          factorCount: 1,
          evaluatedFactorCount: 1,
          availableFactorCount: 1,
          missingFactorCount: 0,
          decisionType: DecisionType.UNDECIDED,
          eligibilityStatus: EligibilityStatus.UNKNOWN,
          confidenceStatus: ConfidenceStatus.PENDING,
          reason: AttendanceDecisionReason.UNKNOWN,
          evaluationTimestamp: '2026-07-01T10:00:04.000Z'
        },
        factors: [factor],
        notes: ['token pending'],
        metadata: {
          evaluationId: 'json-breakdown-evaluation',
          version: CONFIDENCE_ENGINE_VERSION,
          source: 'json-breakdown',
          details: { context: 'json-breakdown', notes: ['json-breakdown-note'], tags: ['json-breakdown-tag'] }
        }
      })
    });

    const parsed = JSON.parse(JSON.stringify(result));

    expect(parsed.metadata.evaluationId).toBe('json-eval');
    expect(parsed.confidenceFactors[0].type).toBe(FactorType.TOKEN_VALIDATION);
    expect(parsed.warnings).toEqual(['token pending']);
  });

  it('builds context, request, and response helpers', () => {
    const metadata = createConfidenceEvaluationMetadata({
      evaluationId: 'ctx-eval',
      version: CONFIDENCE_ENGINE_VERSION,
      source: 'context-test',
      details: { context: 'ctx', notes: ['ctx-note'], tags: ['ctx-tag'] }
    });
    const context: ConfidenceEvaluationContext = createConfidenceEvaluationContext({
      sessionId: 'session-1',
      studentId: 'student-1',
      teacherId: 'teacher-1',
      lectureInstanceId: 'lecture-1',
      academicDate: '2026-07-01',
      lectureActive: true,
      evaluatedAt: '2026-07-01T10:00:00.000Z',
      metadata
    });

    const request: ConfidenceEvaluationRequest = createConfidenceEvaluationRequest({ context, metadata });
    const response: ConfidenceEvaluationResponse = createConfidenceEvaluationResponse({
      context,
      result: createConfidenceResult({
        metadata,
        decision: createConfidenceDecision({
          type: DecisionType.UNDECIDED,
          eligibility: createAttendanceEligibility({
            metadata: {
              evaluationId: 'ctx-eligibility-evaluation',
              version: CONFIDENCE_ENGINE_VERSION,
              source: 'context-eligibility',
              details: { rationale: 'context', notes: ['ctx-eligibility-note'], tags: ['ctx-eligibility-tag'] }
            }
          }),
          metadata: {
            evaluationId: 'ctx-decision-evaluation',
            version: CONFIDENCE_ENGINE_VERSION,
            source: 'context-decision',
            details: { rationale: 'context', notes: ['ctx-decision-note'], tags: ['ctx-decision-tag'] }
          }
        }),
        eligibility: createAttendanceEligibility({
          metadata: {
            evaluationId: 'ctx-eligibility-evaluation-2',
            version: CONFIDENCE_ENGINE_VERSION,
            source: 'context-eligibility-2',
            details: { rationale: 'context', notes: ['ctx-eligibility-note-2'], tags: ['ctx-eligibility-tag-2'] }
          }
        }),
        breakdown: createConfidenceBreakdown({
          summary: {
            factorCount: 0,
            evaluatedFactorCount: 0,
            availableFactorCount: 0,
            missingFactorCount: 0,
            decisionType: DecisionType.UNDECIDED,
            eligibilityStatus: EligibilityStatus.UNKNOWN,
            confidenceStatus: ConfidenceStatus.PENDING,
            reason: AttendanceDecisionReason.UNKNOWN,
            evaluationTimestamp: '2026-07-01T10:00:05.000Z'
          },
          metadata: {
            evaluationId: 'ctx-breakdown-evaluation',
            version: CONFIDENCE_ENGINE_VERSION,
            source: 'context-breakdown',
            details: { context: 'context', notes: ['ctx-breakdown-note'], tags: ['ctx-breakdown-tag'] }
          }
        })
      }),
      metadata
    });

    expect(request.context.sessionId).toBe('session-1');
    expect(request.metadata.evaluationId).toBe('ctx-eval');
    expect(response.context.lectureActive).toBe(true);
    expect(response.result.metadata.evaluationId).toBe('ctx-eval');
  });

  it('keeps builder helper aliases compatible for future extension points', () => {
    const factorContribution: FactorContribution = createConfidenceFactor({
      type: FactorType.DAILY_REGISTRATION,
      name: 'Daily Registration',
      metadata: {
        evaluationId: 'daily-factor-evaluation',
        version: CONFIDENCE_ENGINE_VERSION,
        source: 'daily-factor',
        details: { signal: 'daily', notes: ['daily-note'], tags: ['daily-tag'] }
      }
    });

    expect(factorContribution.name).toBe('Daily Registration');
    expect(factorContribution.score).toBeNull();
  });

  it('declares the ConfidenceEngine interface contract shape', () => {
    const engine: ConfidenceEngine | null = null;
    expect(engine).toBeNull();
  });

  it('exports the centralized confidence engine version constant', () => {
    expect(CONFIDENCE_ENGINE_VERSION).toBe('1.0');
  });
});
