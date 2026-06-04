import { fetchCandidateFingerprints } from './fingerprintService';

type Candidate = {
  id: string;
  classroom_id: string | null;
  fingerprint_data: { [bssid: string]: number };
  sample_type: string;
};

function euclideanDistance(a: { [k: string]: number }, b: { [k: string]: number }) {
  const keys = new Set<string>([...Object.keys(a), ...Object.keys(b)]);
  let sum = 0;
  for (const k of keys) {
    const va = a[k] !== undefined ? a[k] : -100; // missing AP -> -100 dBm
    const vb = b[k] !== undefined ? b[k] : -100;
    const d = va - vb;
    sum += d * d;
  }
  return Math.sqrt(sum);
}

export async function matchFingerprint(input: { [bssid: string]: number }, k = 3) {
  const bssids = Object.keys(input).slice(0, 50);
  const candidates: Candidate[] = await fetchCandidateFingerprints(bssids, 2000);
  const scored = candidates.map((c) => ({
    id: c.id,
    classroom_id: c.classroom_id,
    distance: euclideanDistance(input, c.fingerprint_data),
    sample_type: c.sample_type
  }));
  scored.sort((x, y) => x.distance - y.distance);
  const neighbors = scored.slice(0, k);
  return { neighbors };
}

export { euclideanDistance };
