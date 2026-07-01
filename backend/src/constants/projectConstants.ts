export type WifiSimilarityClassification = 'IN_CLASSROOM' | 'NEAR_CLASSROOM' | 'OUTSIDE_CLASSROOM' | 'UNKNOWN';

export const PROJECT_CONSTANTS = {
  WIFI_SIMILARITY: {
    ACTIVE_ALGORITHM: 'JACCARD' as const,
    THRESHOLDS: {
      IN_CLASSROOM_MIN: 85,
      NEAR_CLASSROOM_MIN: 70
    }
  }
} as const;
