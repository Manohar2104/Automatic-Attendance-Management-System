import { applySoftRangeLimit, euclideanDistance } from '../src/services/matchingService';
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

  it('keeps only neighbors within the additive soft range of the closest match', () => {
    const neighbors = [
      { id: '1', distance: 10 },
      { id: '2', distance: 14.9 },
      { id: '3', distance: 15.1 }
    ];
    process.env.FINGERPRINT_SOFT_RANGE_THRESHOLD = '5';
    const filtered = applySoftRangeLimit(neighbors as any);
    expect((filtered as any[]).map((n) => n.id)).toEqual(['1', '2']);
  });

  it('removes noisy distant neighbors and keeps confidence stable or improved', () => {
    process.env.FINGERPRINT_SOFT_RANGE_THRESHOLD = '5';
    const neighbors = [
      { id: 'near-1', classroom_id: 'c1', distance: 4, sample_type: 'POSITIVE' },
      { id: 'near-2', classroom_id: 'c1', distance: 6, sample_type: 'POSITIVE' },
      { id: 'noisy-far', classroom_id: 'c2', distance: 18, sample_type: 'POSITIVE' }
    ];
    const filtered = applySoftRangeLimit(neighbors as any) as any;
    const unfilteredScore = computeConfidence(neighbors as any).score;
    const filteredScore = computeConfidence(filtered).score;
    expect(filtered.map((n: any) => n.id)).toEqual(['near-1', 'near-2']);
    expect(filteredScore).toBeGreaterThanOrEqual(unfilteredScore);
  });

  it('preserves NEGATIVE penalties after soft-range filtering', () => {
    process.env.FINGERPRINT_SOFT_RANGE_THRESHOLD = '5';
    const baseNeighbors = [
      { id: '1', classroom_id: 'c1', distance: 4, sample_type: 'POSITIVE' },
      { id: '2', classroom_id: 'c1', distance: 6, sample_type: 'POSITIVE' }
    ];
    const withNegative = [
      ...baseNeighbors,
      { id: 'neg', classroom_id: 'c1', distance: 5, sample_type: 'NEGATIVE' }
    ];
    const baseScore = computeConfidence(applySoftRangeLimit(baseNeighbors as any) as any).score;
    const negScore = computeConfidence(applySoftRangeLimit(withNegative as any) as any).score;
    expect(negScore).toBeLessThanOrEqual(baseScore);
  });
});
