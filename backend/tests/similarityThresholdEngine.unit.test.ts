import {
  classifyLocationFromSimilarity,
  getDefaultSimilarityThresholds,
  validateSimilarityThresholds
} from '../src/services/similarityThresholdEngine';

describe('similarity threshold engine', () => {
  it('loads default thresholds from project constants', () => {
    const thresholds = getDefaultSimilarityThresholds();
    expect(thresholds.inClassroomMin).toBe(85);
    expect(thresholds.nearClassroomMin).toBe(70);
  });

  it('validates valid threshold ranges', () => {
    const validated = validateSimilarityThresholds({ inClassroomMin: 90, nearClassroomMin: 70 });
    expect(validated.inClassroomMin).toBe(90);
    expect(validated.nearClassroomMin).toBe(70);
  });

  it('rejects conflicting thresholds', () => {
    expect(() => validateSimilarityThresholds({ inClassroomMin: 70, nearClassroomMin: 70 })).toThrow('SIMILARITY_THRESHOLD_CONFLICT');
  });

  it('classifies score bands correctly', () => {
    expect(classifyLocationFromSimilarity(90)).toBe('IN_CLASSROOM');
    expect(classifyLocationFromSimilarity(75)).toBe('NEAR_CLASSROOM');
    expect(classifyLocationFromSimilarity(65)).toBe('OUTSIDE_CLASSROOM');
  });
});
