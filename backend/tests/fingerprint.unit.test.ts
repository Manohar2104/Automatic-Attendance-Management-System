import { euclideanDistance } from '../src/services/matchingService';
import { computeConfidence } from '../src/services/confidenceService';

describe('Fingerprint matching utilities', () => {
  it('computes Euclidean distance for overlapping and missing APs', () => {
    const a = { 'aa:bb': -50, 'cc:dd': -60 };
    const b = { 'aa:bb': -48, 'ee:ff': -70 };
    const d = euclideanDistance(a, b);
    expect(d).toBeGreaterThan(0);
  });

  it('computes confidence from neighbors', () => {
    const neighbors = [
      { id: '1', classroom_id: 'c1', distance: 5, sample_type: 'POSITIVE' },
      { id: '2', classroom_id: 'c1', distance: 7, sample_type: 'POSITIVE' },
      { id: '3', classroom_id: 'c2', distance: 20, sample_type: 'NEGATIVE' }
    ];
    const { score, breakdown } = computeConfidence(neighbors as any);
    expect(score).toBeGreaterThanOrEqual(0);
    expect(breakdown).toHaveProperty('neighbors');
  });

  it('reduces score when a strong NEGATIVE sample exists for the same classroom', () => {
    const baseNeighbors = [
      { id: '1', classroom_id: 'c1', distance: 5, sample_type: 'POSITIVE' },
      { id: '2', classroom_id: 'c1', distance: 7, sample_type: 'POSITIVE' }
    ];
    const withNegative = [
      ...baseNeighbors,
      { id: 'neg', classroom_id: 'c1', distance: 3, sample_type: 'NEGATIVE' }
    ];
    const baseScore = computeConfidence(baseNeighbors as any).score;
    const negScore = computeConfidence(withNegative as any).score;
    expect(negScore).toBeLessThanOrEqual(baseScore);
  });
});
