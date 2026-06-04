import { pool } from '../config/db';

export type FingerprintSample = {
  classroom_id?: string | null;
  sample_type: 'POSITIVE' | 'NEGATIVE';
  fingerprint_data: { [bssid: string]: number }; // bssid -> rssi
  bssid?: string | null;
  ssid?: string | null;
  rssi?: number | null;
};

export async function registerFingerprint(sample: FingerprintSample) {
  const q = `INSERT INTO fingerprints (classroom_id, bssid, ssid, fingerprint_data, sample_type, rssi, created_at)
  VALUES ($1, $2, $3, $4, $5, $6, now()) RETURNING id`;
  const client = await pool.connect();
  try {
    const r = await client.query(q, [
      sample.classroom_id || null,
      sample.bssid || null,
      sample.ssid || null,
      sample.fingerprint_data,
      sample.sample_type,
      sample.rssi || null
    ]);
    return r.rows[0].id as string;
  } finally {
    client.release();
  }
}

export async function fetchCandidateFingerprints(bssids: string[], limit = 1000) {
  // Candidate selection using JSONB key existence plus bssid column fallback.
  // Uses the `?|` operator to find rows where fingerprint_data has any of the provided BSSIDs as keys.
  const client = await pool.connect();
  try {
    if (!bssids || bssids.length === 0) {
      const r0 = await client.query(`SELECT * FROM fingerprints ORDER BY created_at DESC LIMIT $1`, [limit]);
      return r0.rows;
    }
    // Prefer JSONB key-existence match; include bssid column fallback for legacy rows.
    const r = await client.query(
      `SELECT * FROM fingerprints WHERE (fingerprint_data ?| $1) OR bssid = ANY($2) ORDER BY created_at DESC LIMIT $3`,
      [bssids, bssids, limit]
    );
    if ((r.rowCount ?? 0) > 0) return r.rows;
    // fallback: return recent samples
    const r2 = await client.query(`SELECT * FROM fingerprints ORDER BY created_at DESC LIMIT $1`, [limit]);
    return r2.rows;
  } finally {
    client.release();
  }
}
