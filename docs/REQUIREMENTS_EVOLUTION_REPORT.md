# Requirements Evolution Report

This report documents places where the working implementation is stronger than the current wording in [Requirements.md](Requirements.md), especially for the Wi-Fi fingerprint engine.

## Wi-Fi Fingerprinting Evolution

### Current documented approach in Requirements.md

The current requirements still describe a more classical classroom fingerprinting path centered on:

- mean RSSI aggregation per BSSID across samples
- shared-BSSID matching for classification
- vote-style k-NN scoring for inside/outside decisions

### Working implementation baseline

The implemented backend fingerprint engine uses a stronger and more expressive approach:

- JSONB RSSI vectors stored per fingerprint sample
- Euclidean distance over the union of observed BSSIDs
- additive Soft Range Limited k-NN filtering
- weighted k-NN ranking
- POSITIVE and NEGATIVE fingerprint samples
- NEGATIVE fingerprint penalties in the confidence engine
- confidence aggregation with interpretable breakdown data

### Why the implementation is treated as the baseline

- It preserves more information than mean-RSSI averaging by keeping sample-level JSONB vectors.
- It is already implemented and unit-tested, so downgrading to a simpler model would discard working behavior.
- The additive soft-range filter improves robustness against noisy distant samples without changing the existing architecture.
- NEGATIVE samples provide explicit disconfirming evidence, which is useful for reducing false positives in real indoor localization.

### Documentation decision

For the frozen backend baseline, the enhanced implementation should be treated as the canonical Phase 4 behavior:

- Matching stage: Euclidean distance + additive soft-range filter + weighted k-NN
- Confidence stage: negative-aware aggregation with preserved penalties
- Storage stage: JSONB RSSI vectors with POSITIVE/NEGATIVE samples

### What this means for requirements tracking

- The current Requirements.md remains the source of truth for future work.
- For the working baseline, documentation should not downgrade the implemented fingerprint engine to an older mean-RSSI or vote-only model.
- Future phases should build on the enhanced implementation rather than replacing it unless a deliberate requirements revision is approved.

## Other Implementation Strengths

- JWT auth requires `JWT_SECRET` with no insecure fallback.
- Refresh tokens use hashed storage and theft detection.
- Tests for password hashing and fingerprint utilities already pass.

## Freeze Interpretation

- Fingerprint Engine: frozen as implemented
- Confidence Engine: frozen as implemented
- Authentication baseline: usable and documented, with remaining schema reconciliation noted separately
