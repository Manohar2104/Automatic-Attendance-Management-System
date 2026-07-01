import { PROJECT_CONSTANTS, type WifiSimilarityClassification } from '../constants/projectConstants';

export type SimilarityThresholds = {
  inClassroomMin: number;
  nearClassroomMin: number;
};

export function getDefaultSimilarityThresholds(): SimilarityThresholds {
  return {
    inClassroomMin: PROJECT_CONSTANTS.WIFI_SIMILARITY.THRESHOLDS.IN_CLASSROOM_MIN,
    nearClassroomMin: PROJECT_CONSTANTS.WIFI_SIMILARITY.THRESHOLDS.NEAR_CLASSROOM_MIN
  };
}

export function validateSimilarityThresholds(input: SimilarityThresholds): SimilarityThresholds {
  const inClassroomMin = Number(input.inClassroomMin);
  const nearClassroomMin = Number(input.nearClassroomMin);

  if (!Number.isFinite(inClassroomMin) || !Number.isFinite(nearClassroomMin)) {
    throw new Error('INVALID_SIMILARITY_THRESHOLD');
  }

  if (inClassroomMin < 0 || inClassroomMin > 100 || nearClassroomMin < 0 || nearClassroomMin > 100) {
    throw new Error('INVALID_SIMILARITY_THRESHOLD_RANGE');
  }

  if (nearClassroomMin >= inClassroomMin) {
    throw new Error('SIMILARITY_THRESHOLD_CONFLICT');
  }

  return {
    inClassroomMin,
    nearClassroomMin
  };
}

export function classifyLocationFromSimilarity(
  score: number | null | undefined,
  thresholds: SimilarityThresholds = getDefaultSimilarityThresholds()
): WifiSimilarityClassification {
  if (!Number.isFinite(score)) {
    return 'UNKNOWN';
  }

  const validated = validateSimilarityThresholds(thresholds);
  const normalizedScore = Number(score);

  if (normalizedScore >= validated.inClassroomMin) {
    return 'IN_CLASSROOM';
  }

  if (normalizedScore >= validated.nearClassroomMin) {
    return 'NEAR_CLASSROOM';
  }

  return 'OUTSIDE_CLASSROOM';
}
