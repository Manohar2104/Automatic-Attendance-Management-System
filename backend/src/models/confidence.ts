export const CONFIDENCE_ENGINE_VERSION = '1.0' as const;

export enum ConfidenceStatus {
  UNKNOWN = 'UNKNOWN',
  PENDING = 'PENDING',
  VALID = 'VALID',
  DEGRADED = 'DEGRADED',
  FAILED = 'FAILED'
}

export enum DecisionType {
  UNDECIDED = 'UNDECIDED',
  PRESENT = 'PRESENT',
  PARTIAL = 'PARTIAL',
  ABSENT = 'ABSENT',
  REVIEW = 'REVIEW'
}

export enum AttendanceDecisionReason {
  UNKNOWN = 'UNKNOWN',
  PENDING_CONFIGURATION = 'PENDING_CONFIGURATION',
  INSUFFICIENT_EVIDENCE = 'INSUFFICIENT_EVIDENCE',
  DEVICE_BINDING_CONFIRMED = 'DEVICE_BINDING_CONFIRMED',
  DEVICE_BINDING_MISSING = 'DEVICE_BINDING_MISSING',
  TEACHER_PRESENCE_CONFIRMED = 'TEACHER_PRESENCE_CONFIRMED',
  TEACHER_REFERENCE_AVAILABLE = 'TEACHER_REFERENCE_AVAILABLE',
  WIFI_SIMILARITY_HIGH = 'WIFI_SIMILARITY_HIGH',
  WIFI_SIMILARITY_LOW = 'WIFI_SIMILARITY_LOW',
  HEARTBEAT_CONTINUITY_OK = 'HEARTBEAT_CONTINUITY_OK',
  TOKEN_VALIDATED = 'TOKEN_VALIDATED',
  DAILY_REGISTRATION_VALID = 'DAILY_REGISTRATION_VALID',
  LECTURE_ACTIVE = 'LECTURE_ACTIVE',
  FUTURE_BLE_SIGNAL = 'FUTURE_BLE_SIGNAL',
  FUTURE_MOTION_SIGNAL = 'FUTURE_MOTION_SIGNAL'
}

export enum FactorType {
  DEVICE_BINDING = 'DEVICE_BINDING',
  TEACHER_PRESENCE = 'TEACHER_PRESENCE',
  TEACHER_REFERENCE_FINGERPRINT = 'TEACHER_REFERENCE_FINGERPRINT',
  WIFI_SIMILARITY = 'WIFI_SIMILARITY',
  HEARTBEAT_CONTINUITY = 'HEARTBEAT_CONTINUITY',
  TOKEN_VALIDATION = 'TOKEN_VALIDATION',
  DAILY_REGISTRATION = 'DAILY_REGISTRATION',
  LECTURE_ACTIVE_STATUS = 'LECTURE_ACTIVE_STATUS',
  FUTURE_BLE = 'FUTURE_BLE',
  FUTURE_MOTION_CORRELATION = 'FUTURE_MOTION_CORRELATION'
}

export enum EligibilityStatus {
  UNKNOWN = 'UNKNOWN',
  ELIGIBLE = 'ELIGIBLE',
  INELIGIBLE = 'INELIGIBLE',
  PENDING = 'PENDING'
}

export interface EvaluationMetadataDetails {
  readonly context: string | null;
  readonly notes: readonly string[];
  readonly tags: readonly string[];
}

export interface FactorMetadataDetails {
  readonly signal: string | null;
  readonly notes: readonly string[];
  readonly tags: readonly string[];
}

export interface DecisionMetadataDetails {
  readonly rationale: string | null;
  readonly notes: readonly string[];
  readonly tags: readonly string[];
}

interface ConfidenceMetadataBase {
  evaluationId: string;
  version: typeof CONFIDENCE_ENGINE_VERSION;
  phase: '4.4';
  source: string;
  createdAt: string;
  tags: readonly string[];
}

export type ConfidenceEvaluationMetadata = Readonly<ConfidenceMetadataBase & {
  details: EvaluationMetadataDetails;
}>;

export type FactorMetadata = Readonly<ConfidenceMetadataBase & {
  details: FactorMetadataDetails;
}>;

export type DecisionMetadata = Readonly<ConfidenceMetadataBase & {
  details: DecisionMetadataDetails;
}>;

export interface ConfidenceEvaluationMetadataInput {
  evaluationId: string;
  version?: typeof CONFIDENCE_ENGINE_VERSION;
  source?: string;
  createdAt?: string;
  tags?: readonly string[];
  details?: Partial<EvaluationMetadataDetails>;
}

export interface FactorMetadataInput {
  evaluationId: string;
  version?: typeof CONFIDENCE_ENGINE_VERSION;
  source?: string;
  createdAt?: string;
  tags?: readonly string[];
  details?: Partial<FactorMetadataDetails>;
}

export interface DecisionMetadataInput {
  evaluationId: string;
  version?: typeof CONFIDENCE_ENGINE_VERSION;
  source?: string;
  createdAt?: string;
  tags?: readonly string[];
  details?: Partial<DecisionMetadataDetails>;
}

export type ConfidenceFactor = Readonly<{
  type: FactorType;
  name: string;
  status: ConfidenceStatus;
  score: number | null;
  details: Readonly<FactorMetadataDetails>;
  timestamp: string;
  metadata: FactorMetadata;
}>;

export type FactorContribution = ConfidenceFactor;

export interface ConfidenceFactorInput {
  type: FactorType;
  name: string;
  status?: ConfidenceStatus;
  score?: number | null;
  details?: Partial<FactorMetadataDetails>;
  timestamp?: string;
  metadata: FactorMetadataInput;
}

export type AttendanceEligibility = Readonly<{
  status: EligibilityStatus;
  reason: AttendanceDecisionReason;
  details: Readonly<DecisionMetadataDetails>;
  timestamp: string;
  metadata: DecisionMetadata;
}>;

export interface AttendanceEligibilityInput {
  status?: EligibilityStatus;
  reason?: AttendanceDecisionReason;
  details?: Partial<DecisionMetadataDetails>;
  timestamp?: string;
  metadata: DecisionMetadataInput;
}

export type ConfidenceDecision = Readonly<{
  type: DecisionType;
  reason: AttendanceDecisionReason;
  eligibility: AttendanceEligibility;
  details: Readonly<DecisionMetadataDetails>;
  timestamp: string;
  metadata: DecisionMetadata;
}>;

export interface ConfidenceDecisionInput {
  type?: DecisionType;
  reason?: AttendanceDecisionReason;
  eligibility?: AttendanceEligibility;
  details?: Partial<DecisionMetadataDetails>;
  timestamp?: string;
  metadata: DecisionMetadataInput;
}

export type ConfidenceBreakdown = Readonly<{
  summary: ConfidenceBreakdownSummary;
  factors: readonly ConfidenceFactor[];
  notes: readonly string[];
  metadata: ConfidenceEvaluationMetadata;
}>;

export interface ConfidenceBreakdownSummary {
  readonly factorCount: number;
  readonly evaluatedFactorCount: number;
  readonly availableFactorCount: number;
  readonly missingFactorCount: number;
  readonly decisionType: DecisionType;
  readonly eligibilityStatus: EligibilityStatus;
  readonly confidenceStatus: ConfidenceStatus;
  readonly reason: AttendanceDecisionReason;
  readonly evaluationTimestamp: string;
}

export interface ConfidenceBreakdownInput {
  summary: ConfidenceBreakdownSummary;
  factors?: readonly ConfidenceFactor[];
  notes?: readonly string[];
  metadata?: ConfidenceEvaluationMetadataInput;
}

export type ConfidenceResult = Readonly<{
  overallConfidence: number | null;
  decision: ConfidenceDecision;
  eligibility: AttendanceEligibility;
  breakdown: ConfidenceBreakdown;
  warnings: readonly string[];
  metadata: ConfidenceEvaluationMetadata;
  evaluationTimestamp: string;
  algorithmVersion: typeof CONFIDENCE_ENGINE_VERSION;
  confidenceFactors: readonly ConfidenceFactor[];
  status: ConfidenceStatus;
}>;

export interface ConfidenceResultInput {
  overallConfidence?: number | null;
  decision?: ConfidenceDecision;
  eligibility?: AttendanceEligibility;
  breakdown?: ConfidenceBreakdown;
  warnings?: readonly string[];
  metadata?: ConfidenceEvaluationMetadataInput;
  evaluationTimestamp?: string;
  algorithmVersion?: typeof CONFIDENCE_ENGINE_VERSION;
  confidenceFactors?: readonly ConfidenceFactor[];
  status?: ConfidenceStatus;
}

export type ConfidenceEvaluationContext = Readonly<{
  sessionId: string;
  studentId: string;
  teacherId: string | null;
  lectureInstanceId: string;
  academicDate: string | null;
  lectureActive: boolean;
  evaluatedAt: string;
  deviceBinding: ConfidenceFactor | null;
  teacherPresence: ConfidenceFactor | null;
  teacherReferenceFingerprint: ConfidenceFactor | null;
  wifiSimilarity: ConfidenceFactor | null;
  heartbeatContinuity: ConfidenceFactor | null;
  tokenValidation: ConfidenceFactor | null;
  dailyRegistration: ConfidenceFactor | null;
  futureBle: ConfidenceFactor | null;
  futureMotionCorrelation: ConfidenceFactor | null;
  metadata: ConfidenceEvaluationMetadata;
}>;

export interface ConfidenceEvaluationContextInput {
  sessionId: string;
  studentId: string;
  teacherId?: string | null;
  lectureInstanceId: string;
  academicDate?: string | null;
  lectureActive?: boolean;
  evaluatedAt?: string;
  deviceBinding?: ConfidenceFactor | null;
  teacherPresence?: ConfidenceFactor | null;
  teacherReferenceFingerprint?: ConfidenceFactor | null;
  wifiSimilarity?: ConfidenceFactor | null;
  heartbeatContinuity?: ConfidenceFactor | null;
  tokenValidation?: ConfidenceFactor | null;
  dailyRegistration?: ConfidenceFactor | null;
  futureBle?: ConfidenceFactor | null;
  futureMotionCorrelation?: ConfidenceFactor | null;
  metadata?: ConfidenceEvaluationMetadataInput;
}

export interface ConfidenceEvaluationRequest {
  context: ConfidenceEvaluationContext;
  metadata: ConfidenceEvaluationMetadata;
}

export interface ConfidenceEvaluationRequestInput {
  context: ConfidenceEvaluationContext;
  metadata?: ConfidenceEvaluationMetadataInput;
}

export interface ConfidenceEvaluationResponse {
  context: ConfidenceEvaluationContext;
  result: ConfidenceResult;
  metadata: ConfidenceEvaluationMetadata;
}

export interface ConfidenceEvaluationResponseInput {
  context: ConfidenceEvaluationContext;
  result: ConfidenceResult;
  metadata?: ConfidenceEvaluationMetadataInput;
}

export interface ConfidenceEngine {
  evaluate(request: ConfidenceEvaluationRequest): Promise<ConfidenceEvaluationResponse> | ConfidenceEvaluationResponse;
  validateInputs(request: ConfidenceEvaluationRequest): AttendanceEligibility;
  buildBreakdown(request: ConfidenceEvaluationRequest): ConfidenceBreakdown;
}

function deepFreeze<T>(value: T): T {
  if (value === null || typeof value !== 'object' || Object.isFrozen(value)) {
    return value;
  }

  const target = value as Record<string, unknown>;
  Object.freeze(target);

  for (const key of Object.keys(target)) {
    deepFreeze(target[key]);
  }

  return value;
}

function freezeArray<T>(items: readonly T[] = []): readonly T[] {
  return deepFreeze([...items]);
}

export function createConfidenceEvaluationMetadata(input: ConfidenceEvaluationMetadataInput): ConfidenceEvaluationMetadata {
  return deepFreeze({
    evaluationId: input.evaluationId,
    version: input.version ?? CONFIDENCE_ENGINE_VERSION,
    phase: '4.4',
    source: input.source ?? 'confidence-engine',
    createdAt: input.createdAt ?? new Date().toISOString(),
    tags: freezeArray(input.tags ?? []),
    details: deepFreeze({
      context: input.details?.context ?? null,
      notes: freezeArray(input.details?.notes ?? []),
      tags: freezeArray(input.details?.tags ?? [])
    })
  });
}

export function createFactorMetadata(input: FactorMetadataInput): FactorMetadata {
  return deepFreeze({
    evaluationId: input.evaluationId,
    version: input.version ?? CONFIDENCE_ENGINE_VERSION,
    phase: '4.4',
    source: input.source ?? 'confidence-factor',
    createdAt: input.createdAt ?? new Date().toISOString(),
    tags: freezeArray(input.tags ?? []),
    details: deepFreeze({
      signal: input.details?.signal ?? null,
      notes: freezeArray(input.details?.notes ?? []),
      tags: freezeArray(input.details?.tags ?? [])
    })
  });
}

export function createDecisionMetadata(input: DecisionMetadataInput): DecisionMetadata {
  return deepFreeze({
    evaluationId: input.evaluationId,
    version: input.version ?? CONFIDENCE_ENGINE_VERSION,
    phase: '4.4',
    source: input.source ?? 'confidence-decision',
    createdAt: input.createdAt ?? new Date().toISOString(),
    tags: freezeArray(input.tags ?? []),
    details: deepFreeze({
      rationale: input.details?.rationale ?? null,
      notes: freezeArray(input.details?.notes ?? []),
      tags: freezeArray(input.details?.tags ?? [])
    })
  });
}

export function createConfidenceFactor(input: ConfidenceFactorInput): ConfidenceFactor {
  return deepFreeze({
    type: input.type,
    name: input.name,
    status: input.status ?? ConfidenceStatus.PENDING,
    score: input.score ?? null,
    details: deepFreeze({
      signal: input.details?.signal ?? null,
      notes: freezeArray(input.details?.notes ?? []),
      tags: freezeArray(input.details?.tags ?? [])
    }),
    timestamp: input.timestamp ?? new Date().toISOString(),
    metadata: createFactorMetadata(input.metadata)
  });
}

export function createAttendanceEligibility(input: AttendanceEligibilityInput): AttendanceEligibility {
  return deepFreeze({
    status: input.status ?? EligibilityStatus.UNKNOWN,
    reason: input.reason ?? AttendanceDecisionReason.UNKNOWN,
    details: deepFreeze({
      rationale: input.details?.rationale ?? null,
      notes: freezeArray(input.details?.notes ?? []),
      tags: freezeArray(input.details?.tags ?? [])
    }),
    timestamp: input.timestamp ?? new Date().toISOString(),
    metadata: createDecisionMetadata(input.metadata)
  });
}

export function createConfidenceDecision(input: ConfidenceDecisionInput): ConfidenceDecision {
  const eligibility = input.eligibility;
  if (!eligibility) {
    throw new Error('MISSING_CONFIDENCE_ELIGIBILITY');
  }

  return deepFreeze({
    type: input.type ?? DecisionType.UNDECIDED,
    reason: input.reason ?? AttendanceDecisionReason.UNKNOWN,
    eligibility,
    details: deepFreeze({
      rationale: input.details?.rationale ?? null,
      notes: freezeArray(input.details?.notes ?? []),
      tags: freezeArray(input.details?.tags ?? [])
    }),
    timestamp: input.timestamp ?? new Date().toISOString(),
    metadata: createDecisionMetadata(input.metadata)
  });
}

export function createConfidenceBreakdown(input: ConfidenceBreakdownInput): ConfidenceBreakdown {
  const factors = freezeArray(input.factors ?? []);
  const notes = freezeArray(input.notes ?? []);
  return deepFreeze({
    summary: input.summary,
    factors,
    notes,
    metadata: createConfidenceEvaluationMetadata(input.metadata as ConfidenceEvaluationMetadataInput)
  });
}

export function createConfidenceResult(input: ConfidenceResultInput): ConfidenceResult {
  const factors = freezeArray(input.confidenceFactors ?? []);
  const decision = input.decision;
  const eligibility = input.eligibility;
  const breakdown = input.breakdown;

  if (!decision || !eligibility || !breakdown) {
    throw new Error('MISSING_CONFIDENCE_MODEL_COMPONENTS');
  }

  return deepFreeze({
    overallConfidence: input.overallConfidence ?? null,
    decision,
    eligibility,
    breakdown,
    warnings: freezeArray(input.warnings ?? []),
    metadata: createConfidenceEvaluationMetadata(input.metadata as ConfidenceEvaluationMetadataInput),
    evaluationTimestamp: input.evaluationTimestamp ?? new Date().toISOString(),
    algorithmVersion: input.algorithmVersion ?? CONFIDENCE_ENGINE_VERSION,
    confidenceFactors: factors,
    status: input.status ?? ConfidenceStatus.PENDING
  });
}

export function createConfidenceEvaluationContext(input: ConfidenceEvaluationContextInput): ConfidenceEvaluationContext {
  return deepFreeze({
    sessionId: input.sessionId,
    studentId: input.studentId,
    teacherId: input.teacherId ?? null,
    lectureInstanceId: input.lectureInstanceId,
    academicDate: input.academicDate ?? null,
    lectureActive: input.lectureActive ?? false,
    evaluatedAt: input.evaluatedAt ?? new Date().toISOString(),
    deviceBinding: input.deviceBinding ?? null,
    teacherPresence: input.teacherPresence ?? null,
    teacherReferenceFingerprint: input.teacherReferenceFingerprint ?? null,
    wifiSimilarity: input.wifiSimilarity ?? null,
    heartbeatContinuity: input.heartbeatContinuity ?? null,
    tokenValidation: input.tokenValidation ?? null,
    dailyRegistration: input.dailyRegistration ?? null,
    futureBle: input.futureBle ?? null,
    futureMotionCorrelation: input.futureMotionCorrelation ?? null,
    metadata: createConfidenceEvaluationMetadata(input.metadata as ConfidenceEvaluationMetadataInput)
  });
}

export function createConfidenceEvaluationRequest(input: ConfidenceEvaluationRequestInput): ConfidenceEvaluationRequest {
  return deepFreeze({
    context: input.context,
    metadata: createConfidenceEvaluationMetadata(input.metadata as ConfidenceEvaluationMetadataInput)
  });
}

export function createConfidenceEvaluationResponse(input: ConfidenceEvaluationResponseInput): ConfidenceEvaluationResponse {
  return deepFreeze({
    context: input.context,
    result: input.result,
    metadata: createConfidenceEvaluationMetadata(input.metadata as ConfidenceEvaluationMetadataInput)
  });
}
