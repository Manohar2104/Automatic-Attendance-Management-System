import {
  calculateSimilarity,
  CosineSimilarityStrategy,
  JaccardSimilarityStrategy,
  SignalDistanceSimilarityStrategy,
  WeightedRssiSimilarityStrategy
} from '../src/services/wifiSimilarityEngine';

describe('wifi similarity engine', () => {
  it('returns unknown when no APs are available', () => {
    const result = calculateSimilarity([], []);
    expect(result.score).toBe(0);
    expect(result.classification).toBe('UNKNOWN');
    expect(result.provider).toBe('WifiReferenceCollector');
    expect(result.comparisonTimestamp).toEqual(expect.any(String));
    expect(result.confidenceMetadata).toEqual(expect.objectContaining({ status: 'PENDING_PHASE_4_4' }));
  });

  it('returns 100 for complete overlap even if RSSI differs', () => {
    const result = calculateSimilarity(
      [{ bssid: 'AA:BB:CC:DD:EE:FF', rssi: -40, ssid: 'T' }],
      [{ bssid: 'AA:BB:CC:DD:EE:FF', rssi: -85, ssid: 'S' }]
    );

    expect(result.score).toBe(100);
    expect(result.classification).toBe('IN_CLASSROOM');
    expect(result.matchedBssids).toEqual(['aa:bb:cc:dd:ee:ff']);
  });

  it('returns partial score for partial overlap', () => {
    const result = calculateSimilarity(
      [
        { bssid: 'AA:BB:CC:DD:EE:FF', rssi: -50 },
        { bssid: '11:22:33:44:55:66', rssi: -60 }
      ],
      [
        { bssid: 'AA:BB:CC:DD:EE:FF', rssi: -57 },
        { bssid: '77:88:99:AA:BB:CC', rssi: -65 }
      ]
    );

    expect(result.score).toBe(33.33);
    expect(result.classification).toBe('OUTSIDE_CLASSROOM');
    expect(result.missingBssids).toContain('11:22:33:44:55:66');
    expect(result.extraBssids).toContain('77:88:99:aa:bb:cc');
  });

  it('deduplicates duplicate APs before similarity calculation', () => {
    const result = calculateSimilarity(
      [
        { bssid: 'AA:BB:CC:DD:EE:FF', rssi: -50 },
        { bssid: 'AA:BB:CC:DD:EE:FF', rssi: -52 }
      ],
      [{ bssid: 'AA:BB:CC:DD:EE:FF', rssi: -60 }]
    );

    expect(result.score).toBe(100);
  });

  it('rejects non-implemented algorithms while keeping extension points', () => {
    expect(() => calculateSimilarity([], [], { algorithm: 'COSINE' })).toThrow('SIMILARITY_ALGORITHM_NOT_IMPLEMENTED');
  });

  it('ignores malformed/null entries and returns deterministic ordering', () => {
    const result = calculateSimilarity(
      [
        { bssid: 'BB:BB:BB:BB:BB:BB', rssi: -55 },
        null as unknown as any,
        { bssid: 'AA:AA:AA:AA:AA:AA', rssi: -60 },
        { bssid: 'bad', rssi: -60 } as any,
        { bssid: 'BB:BB:BB:BB:BB:BB', rssi: -70 }
      ],
      [
        { bssid: 'AA:AA:AA:AA:AA:AA', rssi: -62 },
        { bssid: 'CC:CC:CC:CC:CC:CC', rssi: -58 }
      ]
    );

    expect(result.matchedBssids).toEqual(['aa:aa:aa:aa:aa:aa']);
    expect(result.missingBssids).toEqual(['bb:bb:bb:bb:bb:bb']);
    expect(result.extraBssids).toEqual(['cc:cc:cc:cc:cc:cc']);
  });

  it('returns an immutable similarity result object', () => {
    const result = calculateSimilarity(
      [{ bssid: 'AA:BB:CC:DD:EE:FF', rssi: -55 }],
      [{ bssid: 'AA:BB:CC:DD:EE:FF', rssi: -58 }]
    );

    expect(Object.isFrozen(result)).toBe(true);
    expect(Object.isFrozen(result.matchedBssids)).toBe(true);
    expect(Object.isFrozen(result.confidenceMetadata)).toBe(true);
  });

  it('exposes metadata for jaccard strategy', () => {
    const strategy = new JaccardSimilarityStrategy();

    expect(strategy.getName()).toBe('Jaccard Similarity');
    expect(strategy.getVersion()).toBe('1.0');
    expect(strategy.getSupportedFeatures()).toEqual({
      bssidComparison: true,
      rssi: false,
      frequency: false,
      ble: false,
      motion: false
    });
  });

  it('exposes placeholder metadata for non-active strategies', () => {
    const strategies = [
      new CosineSimilarityStrategy(),
      new WeightedRssiSimilarityStrategy(),
      new SignalDistanceSimilarityStrategy()
    ];

    for (const strategy of strategies) {
      expect(strategy.getVersion()).toBe('0.0-placeholder');
      expect(strategy.getSupportedFeatures()).toEqual({
        bssidComparison: false,
        rssi: false,
        frequency: false,
        ble: false,
        motion: false
      });
      expect(() => strategy.compare({ bssids: [] }, { bssids: [] })).toThrow('SIMILARITY_ALGORITHM_NOT_IMPLEMENTED');
    }
  });
});
