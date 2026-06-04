# Phase 4 Report — Wi‑Fi Fingerprint Engine

## Phase Objectives

- Implement a Wi‑Fi fingerprinting engine that can register positive and negative samples, match live scans against stored samples using k‑NN, and return an interpretable confidence score (0–100).
- Ensure negative samples reduce confidence and are treated differently than positive samples.
- Use JSONB storage for RSSI vectors and provide a production-ready candidate selection strategy.

## Wi‑Fi Fingerprinting Overview

Wi‑Fi fingerprinting uses the set of observed access points (BSSIDs) and their RSSI measurements to characterize a physical location (e.g., a classroom). A fingerprint is represented as a sparse vector mapping BSSID → RSSI (dBm). Matching compares a live RSSI vector to stored samples and finds nearest neighbors.

## Fingerprint Architecture

- Data model: every fingerprint sample is stored in `fingerprints` with fields: `id`, `classroom_id`, `bssid`, `ssid`, `fingerprint_data` (JSONB), `sample_type` (POSITIVE/NEGATIVE), `rssi`, `created_at`.
- Services:
  - Registration service: validate and insert samples.
  - Candidate selection: JSONB-aware filter to reduce search set.
  - Matching service: compute Euclidean distance against candidates and return top‑k neighbors.
  - Confidence service: convert neighbor distances & sample types into a 0–100 confidence score.

## Positive Fingerprints

- `POSITIVE` samples represent authorized/expected signals for a classroom.
- They contribute positively to class weight when computing a match (higher weight when closer in RSSI space).

## Negative Fingerprints

- `NEGATIVE` samples represent signals observed in non-classroom or adversarial contexts (e.g., nearby hallways, other rooms, or deliberate spoofing).
- Negative samples reduce confidence for a class: they are NOT treated as ordinary positives. Instead they subtract from positive weight using a penalty multiplier to model disproving evidence.

### Penalty Strategy

- Each neighbor contributes an inverse-distance weight: $w_i = \frac{1}{d_i + 1}$, where $d_i$ is Euclidean distance.
- Positive contribution for class $c$ is $P_c = \sum_{i \in POS(c)} w_i$.
- Negative contribution for class $c$ is $N_c = \sum_{i \in NEG(c)} w_i$.
- We apply a penalty multiplier $\alpha > 1$ (implementation default $\alpha = 1.5$) to make negative evidence stronger per-unit weight.
- Net class weight: $W_c = P_c - \alpha \cdot N_c$.
- If $W_c \le 0$ for all classes, the system treats there being no reliable positive signal.

## RSSI Vector Storage (JSONB)

- Fingerprints are stored as JSONB objects mapping BSSID → RSSI (numbers). Example:

```json
{ "aa:bb:cc:dd:ee:ff": -48, "11:22:33:44:55:66": -72 }
```

- Advantages: flexible schema, fast writes, good compatibility with PostgreSQL/Neon.

## k‑NN Matching Algorithm

- Default: weighted k‑NN with $k = 3$ (configurable).
- Steps:
  1. Select candidate fingerprints using a JSONB key‑existence filter (fast prefilter).
  2. For each candidate compute Euclidean distance to the input vector.
  3. Convert distance to weight using inverse-distance weighting: $w_i = 1/(d_i + 1)$.
  4. Aggregate positive and negative weights per class and compute net weights using the penalty multiplier.
  5. Choose top class by net weight and compute a confidence score.

### Weighted k‑NN (mathematical)

Given neighbors $i = 1..k$ with distances $d_i$ and class membership, weight each neighbor by
$$w_i = \frac{1}{d_i + 1}.$$ 
The class score is the (possibly penalized) sum of weights for that class, and the classification follows the class with maximum (net) weight.

## Euclidean Distance Calculation

Let the input fingerprint be vector $\mathbf{x}$ and candidate fingerprint $\mathbf{y}$. Let the union of observed BSSIDs across the two samples be indexed by $j$. For missing BSSIDs, the implementation uses a default RSSI value of $-100\,$dBm.

The Euclidean distance is:
$$
d(\mathbf{x},\mathbf{y}) = \sqrt{\sum_j (x_j - y_j)^2}.
$$

This distance captures overall difference in RSSI across all APs and is robust when many APs overlap.

## Confidence Score Calculation

Confidence is a normalized, distance‑aware, penalty‑aware score in $[0,100]$ computed as follows.

1. Compute weights $w_i = 1/(d_i + 1)$ for top‑k neighbors.
2. Compute positive and negative aggregates per class:
   $$P_c = \sum_{i\in POS(c)} w_i, \quad N_c = \sum_{i\in NEG(c)} w_i.$$ 
3. Apply negative penalty $\alpha$ and compute net class weight:
   $$W_c = P_c - \alpha N_c.$$ 
4. Effective normalization term (used for percentage):
   $$T_{eff} = P_{tot} + \alpha N_{tot} + \epsilon,$$ where $P_{tot}=\sum_c P_c$, $N_{tot}=\sum_c N_c$, and $\epsilon$ is a tiny constant to avoid divide‑by‑zero.
5. Base score (percentage of normalized top net weight):
   $$S_{base} = \max\left(0, \frac{\max_c W_c}{T_{eff}}\right) \times 100.$$
6. Distance scaling factor to penalize large average neighbor distances:
   $$\text{distanceScale} = \frac{1}{1 + \frac{\overline{d}}{50}},$$ where $\overline{d}$ is average neighbor distance.
7. Final score:
   $$\text{score} = \mathrm{round}\left(S_{base} \times \text{distanceScale}\right).$$

This produces integer scores in $[0,100]$ where negatives reduce the numerator and increase the denominator (via penalty) so that negative evidence actively suppresses confidence.

## Candidate Selection Strategy

- The service prefers JSONB‑aware key existence filtering to find candidates that share AP keys with the input. Example SQL (Postgres/Neon):

```sql
SELECT * FROM fingerprints
WHERE (fingerprint_data ?| array[:bssid_list])
   OR bssid = ANY(:bssid_list)
ORDER BY created_at DESC
LIMIT :limit;
```

- This leverages the `?|` operator which checks if the JSONB object has any of the provided keys.

### Recommended Production Indexes

```sql
CREATE INDEX idx_fingerprint_data_gin ON fingerprints USING GIN (fingerprint_data);
CREATE INDEX idx_fingerprints_bssid ON fingerprints (bssid);
CREATE INDEX idx_fingerprints_created_at ON fingerprints (created_at DESC);
```

- For very large datasets or low‑latency NN queries, export or vectorize fingerprints and use `pgvector` or an ANN engine (HNSW, Faiss). JSONB is flexible but not optimized for high‑dimensional ANN.

## API Endpoints

- `POST /fingerprints/register` — register a fingerprint sample (POSITIVE or NEGATIVE). Body: `{ classroom_id?, sample_type: 'POSITIVE'|'NEGATIVE', fingerprint_data: {bssid: rssi, ...}, bssid?, ssid? }`.
- `POST /fingerprints/match` — match live scan. Body: `{ fingerprint_data: {...}, k?: number }`. Response: `{ result: { score, breakdown, classroom_id } }`.
- `GET /fingerprints/candidates?bssids=a,b,c` — list candidate rows matching BSSIDs.

## Database Tables Used

- `fingerprints` (primary): stores fingerprint_data JSONB, sample_type, classroom_id, and metadata.
- (Existing auth tables remain unchanged.)

## Security Considerations

- Require authentication & rate limiting for registration and match endpoints to prevent data poisoning and scraping.
- Validate and sanitize BSSID formats; normalize keys to a canonical format (lowercase, colon‑separated) before storage.
- Log suspicious events (many NEGATIVE samples for a classroom, sudden changes in fingerprint space) to `security_events`.

## Performance Considerations

- For production workloads consider:
  - GIN index on `fingerprint_data` for JSONB key existence queries.
  - Index on `bssid` and `created_at` for legacy and recency filters.
  - Vectorize fingerprints and use `pgvector` + ivfflat/hnsw for approximate nearest neighbor.
  - Cache common queries and precompute aggregated centroids per classroom for faster scoring.

## Testing Strategy

- Unit tests for:
  - Euclidean distance behavior with missing APs.
  - Matching pipeline (candidate selection → ranking → neighbors).
  - Confidence calculation including NEGATIVE penalties and edge cases (all negatives).
- Integration tests (DB required) for end‑to‑end register → match flows. Run against an isolated test DB and DO NOT apply migrations to production automatically.

## Future Improvements

- Tune penalty multiplier $\alpha$ with labeled ground truth.
- Add ingestion of multiple samples per classroom and compute centroids / clustering for robust templates.
- Implement ANN search (pgvector or external ANN) for scalability.
- Add defenses against poisoning: validation, sample vetting, and human review workflow for negative samples.

---

_Report generated: Phase 4 — Wi‑Fi Fingerprint Engine_
