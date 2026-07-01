import { classifyLocationFromSimilarity } from '../src/services/similarityThresholdEngine';

describe('location classification', () => {
  it('returns unknown for missing score', () => {
    expect(classifyLocationFromSimilarity(null)).toBe('UNKNOWN');
    expect(classifyLocationFromSimilarity(undefined)).toBe('UNKNOWN');
  });

  it('classifies boundary values correctly', () => {
    expect(classifyLocationFromSimilarity(85)).toBe('IN_CLASSROOM');
    expect(classifyLocationFromSimilarity(84)).toBe('NEAR_CLASSROOM');
    expect(classifyLocationFromSimilarity(70)).toBe('NEAR_CLASSROOM');
    expect(classifyLocationFromSimilarity(69.99)).toBe('OUTSIDE_CLASSROOM');
  });
});
