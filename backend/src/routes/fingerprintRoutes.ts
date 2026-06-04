import express from 'express';
import { registerFingerprint, fetchCandidateFingerprints } from '../services/fingerprintService';
import { matchFingerprint } from '../services/matchingService';
import { computeConfidence } from '../services/confidenceService';

const router = express.Router();

router.post('/register', async (req, res) => {
  const payload = req.body; // expect classroom_id, sample_type, fingerprint_data
  if (!payload || !payload.fingerprint_data) return res.status(400).json({ error: 'missing_fingerprint' });
  const id = await registerFingerprint(payload);
  return res.status(201).json({ id });
});

router.post('/match', async (req, res) => {
  const { fingerprint_data, k } = req.body;
  if (!fingerprint_data) return res.status(400).json({ error: 'missing_fingerprint' });
  const { neighbors } = await matchFingerprint(fingerprint_data, k || 3);
  const result = computeConfidence(neighbors);
  return res.json({ result });
});

router.get('/candidates', async (req, res) => {
  const bssids = (req.query.bssids as string || '').split(',').filter(Boolean);
  const rows = await fetchCandidateFingerprints(bssids, 200);
  return res.json({ count: rows.length, rows });
});

export default router;
