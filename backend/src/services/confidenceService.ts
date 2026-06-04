type Neighbor = { id: string; classroom_id: string | null; distance: number; sample_type: string };

// Penalty multiplier applied to NEGATIVE samples. Values >1 make negatives stronger penalties.
const NEGATIVE_PENALTY = 1.5;

export function computeConfidence(neighbors: Neighbor[]) {
  if (!neighbors || neighbors.length === 0) return { score: 0, breakdown: {} };
  const eps = 1e-6;
  // weights = 1 / (distance + 1) to avoid divide-by-zero and keep scale
  const weights = neighbors.map((n) => 1 / (n.distance + 1 + eps));

  // Separate aggregates for POSITIVE and NEGATIVE samples
  const posAgg = new Map<string, number>();
  const negAgg = new Map<string, number>();
  let posTotal = 0;
  let negTotal = 0;

  neighbors.forEach((n, i) => {
    const key = n.classroom_id || 'unknown';
    const w = weights[i];
    if ((n.sample_type || '').toUpperCase() === 'NEGATIVE') {
      negTotal += w;
      negAgg.set(key, (negAgg.get(key) || 0) + w);
    } else {
      posTotal += w;
      posAgg.set(key, (posAgg.get(key) || 0) + w);
    }
  });

  // Compute net weight per classroom: positive weight minus penalty * negative weight
  const classes = new Set<string>([...Array.from(posAgg.keys()), ...Array.from(negAgg.keys())]);
  let topClass: string | null = null;
  let topNetWeight = 0;
  for (const cls of classes) {
    const p = posAgg.get(cls) || 0;
    const n = negAgg.get(cls) || 0;
    const net = p - NEGATIVE_PENALTY * n;
    if (net > topNetWeight) {
      topNetWeight = net;
      topClass = cls === 'unknown' ? null : cls;
    }
  }

  // Effective total: positives plus penalized negatives (used for normalization)
  const effectiveTotal = posTotal + NEGATIVE_PENALTY * negTotal + eps;

  // If topNetWeight is non-positive, we consider there is no positive signal for any classroom
  const baseScore = topNetWeight > 0 ? (topNetWeight / effectiveTotal) * 100 : 0;

  // distance penalty: average distance of neighbors
  const avgDist = neighbors.reduce((s, n) => s + n.distance, 0) / neighbors.length;
  const distanceScale = 1 / (1 + avgDist / 50);
  const score = Math.max(0, Math.round(baseScore * distanceScale));

  const breakdown = {
    baseScore: Math.round(baseScore),
    distanceScale: Number(distanceScale.toFixed(3)),
    avgDistance: Number(avgDist.toFixed(2)),
    posTotal: Number(posTotal.toFixed(4)),
    negTotal: Number(negTotal.toFixed(4)),
    negPenalty: NEGATIVE_PENALTY,
    neighbors: neighbors.map((n) => ({ id: n.id, classroom_id: n.classroom_id, distance: Number(n.distance.toFixed(2)), sample_type: n.sample_type }))
  };
  return { score, breakdown, classroom_id: topClass };
}
