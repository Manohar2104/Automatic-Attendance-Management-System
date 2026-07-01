import { PROJECT_CONSTANTS, type WifiSimilarityClassification } from '../constants/projectConstants';
import {
  classifyLocationFromSimilarity,
  getDefaultSimilarityThresholds,
  type SimilarityThresholds
} from './similarityThresholdEngine';

export type SimilarityAccessPoint = {
  bssid: string;
  ssid?: string | null;
  rssi: number;
};

export type SimilarityAlgorithm = 'JACCARD' | 'COSINE' | 'WEIGHTED_RSSI' | 'SIGNAL_DISTANCE';

export type SimilarityResult = Readonly<{
  score: number;
  algorithm: SimilarityAlgorithm;
  classification: WifiSimilarityClassification;
  provider: string;
  matchedBssids: readonly string[];
  missingBssids: readonly string[];
  extraBssids: readonly string[];
  teacherFingerprintTimestamp: string | null;
  studentFingerprintTimestamp: string | null;
  comparisonTimestamp: string;
  confidenceMetadata: Readonly<Record<string, unknown>>;
}>;

type NormalizedFingerprint = Readonly<{
  bssids: readonly string[];
}>;

type SimilarityStrategyResult = {
  score: number;
  matchedBssids: string[];
  missingBssids: string[];
  extraBssids: string[];
};

type SimilarityStrategyFeatureSupport = Readonly<{
  bssidComparison: boolean;
  rssi: boolean;
  frequency: boolean;
  ble: boolean;
  motion: boolean;
}>;

interface SimilarityStrategy {
  readonly algorithm: SimilarityAlgorithm;
  getName(): string;
  getVersion(): string;
  getSupportedFeatures(): SimilarityStrategyFeatureSupport;
  compare(reference: NormalizedFingerprint, student: NormalizedFingerprint): SimilarityStrategyResult;
}

const BSSID_REGEX = /^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$/;

function normalizeFingerprintInput(fingerprint: SimilarityAccessPoint[]): NormalizedFingerprint {
  const deduped = new Set<string>();

  for (const item of fingerprint) {
    const candidate = typeof item?.bssid === 'string' ? item.bssid.trim().toLowerCase() : '';
    if (BSSID_REGEX.test(candidate)) {
      deduped.add(candidate);
    }
  }

  return {
    bssids: Object.freeze(Array.from(deduped).sort())
  };
}

class JaccardSimilarityStrategy implements SimilarityStrategy {
  readonly algorithm: SimilarityAlgorithm = 'JACCARD';

  getName() {
    return 'Jaccard Similarity';
  }

  getVersion() {
    return '1.0';
  }

  getSupportedFeatures(): SimilarityStrategyFeatureSupport {
    return {
      bssidComparison: true,
      rssi: false,
      frequency: false,
      ble: false,
      motion: false
    };
  }

  compare(reference: NormalizedFingerprint, student: NormalizedFingerprint): SimilarityStrategyResult {
    const referenceSet = new Set(reference.bssids);
    const studentSet = new Set(student.bssids);

    if (referenceSet.size === 0 || studentSet.size === 0) {
      return {
        score: 0,
        matchedBssids: [],
        missingBssids: [...reference.bssids],
        extraBssids: [...student.bssids]
      };
    }

    const matchedBssids = reference.bssids.filter((bssid) => studentSet.has(bssid));
    const missingBssids = reference.bssids.filter((bssid) => !studentSet.has(bssid));
    const extraBssids = student.bssids.filter((bssid) => !referenceSet.has(bssid));

    const unionCount = new Set([...reference.bssids, ...student.bssids]).size;
    const score = unionCount === 0 ? 0 : Number(((matchedBssids.length / unionCount) * 100).toFixed(2));

    return {
      score,
      matchedBssids,
      missingBssids,
      extraBssids
    };
  }
}

class CosineSimilarityStrategy implements SimilarityStrategy {
  readonly algorithm: SimilarityAlgorithm = 'COSINE';

  getName() {
    return 'Cosine Similarity';
  }

  getVersion() {
    return '0.0-placeholder';
  }

  getSupportedFeatures(): SimilarityStrategyFeatureSupport {
    return {
      bssidComparison: false,
      rssi: false,
      frequency: false,
      ble: false,
      motion: false
    };
  }

  compare(_reference: NormalizedFingerprint, _student: NormalizedFingerprint): SimilarityStrategyResult {
    throw new Error('SIMILARITY_ALGORITHM_NOT_IMPLEMENTED');
  }
}

class WeightedRssiSimilarityStrategy implements SimilarityStrategy {
  readonly algorithm: SimilarityAlgorithm = 'WEIGHTED_RSSI';

  getName() {
    return 'Weighted RSSI Similarity';
  }

  getVersion() {
    return '0.0-placeholder';
  }

  getSupportedFeatures(): SimilarityStrategyFeatureSupport {
    return {
      bssidComparison: false,
      rssi: false,
      frequency: false,
      ble: false,
      motion: false
    };
  }

  compare(_reference: NormalizedFingerprint, _student: NormalizedFingerprint): SimilarityStrategyResult {
    throw new Error('SIMILARITY_ALGORITHM_NOT_IMPLEMENTED');
  }
}

class SignalDistanceSimilarityStrategy implements SimilarityStrategy {
  readonly algorithm: SimilarityAlgorithm = 'SIGNAL_DISTANCE';

  getName() {
    return 'Signal Distance Similarity';
  }

  getVersion() {
    return '0.0-placeholder';
  }

  getSupportedFeatures(): SimilarityStrategyFeatureSupport {
    return {
      bssidComparison: false,
      rssi: false,
      frequency: false,
      ble: false,
      motion: false
    };
  }

  compare(_reference: NormalizedFingerprint, _student: NormalizedFingerprint): SimilarityStrategyResult {
    throw new Error('SIMILARITY_ALGORITHM_NOT_IMPLEMENTED');
  }
}

const STRATEGIES: Record<SimilarityAlgorithm, SimilarityStrategy> = {
  JACCARD: new JaccardSimilarityStrategy(),
  COSINE: new CosineSimilarityStrategy(),
  WEIGHTED_RSSI: new WeightedRssiSimilarityStrategy(),
  SIGNAL_DISTANCE: new SignalDistanceSimilarityStrategy()
};

export function calculateSimilarity(
  referenceFingerprint: SimilarityAccessPoint[],
  studentFingerprint: SimilarityAccessPoint[],
  options: {
    algorithm?: SimilarityAlgorithm;
    thresholds?: SimilarityThresholds;
    provider?: string;
    teacherFingerprintTimestamp?: string | null;
    studentFingerprintTimestamp?: string | null;
    comparisonTimestamp?: string;
  } = {}
): SimilarityResult {
  const reference = normalizeFingerprintInput(referenceFingerprint);
  const student = normalizeFingerprintInput(studentFingerprint);

  const algorithm = options.algorithm ?? PROJECT_CONSTANTS.WIFI_SIMILARITY.ACTIVE_ALGORITHM;
  const strategy = STRATEGIES[algorithm];
  if (!strategy) {
    throw new Error('SIMILARITY_STRATEGY_NOT_FOUND');
  }

  const similarity = strategy.compare(reference, student);
  const classification = similarity.matchedBssids.length === 0 && (reference.bssids.length === 0 || student.bssids.length === 0)
    ? 'UNKNOWN'
    : classifyLocationFromSimilarity(similarity.score, options.thresholds ?? getDefaultSimilarityThresholds());

  const result: SimilarityResult = {
    score: similarity.score,
    algorithm,
    classification,
    provider: options.provider ?? 'WifiReferenceCollector',
    matchedBssids: Object.freeze([...similarity.matchedBssids]),
    missingBssids: Object.freeze([...similarity.missingBssids]),
    extraBssids: Object.freeze([...similarity.extraBssids]),
    teacherFingerprintTimestamp: options.teacherFingerprintTimestamp ?? null,
    studentFingerprintTimestamp: options.studentFingerprintTimestamp ?? null,
    comparisonTimestamp: options.comparisonTimestamp ?? new Date().toISOString(),
    confidenceMetadata: Object.freeze({
      status: 'PENDING_PHASE_4_4',
      reason: 'CONFIDENCE_ENGINE_NOT_IMPLEMENTED'
    })
  };

  return Object.freeze(result);
}

export {
  CosineSimilarityStrategy,
  JaccardSimilarityStrategy,
  SignalDistanceSimilarityStrategy,
  WeightedRssiSimilarityStrategy,
  normalizeFingerprintInput
};
