import { pool } from '../config/db';

export async function createDeviceBinding(userId: string, name?: string, fingerprint?: any, ip?: string) {
  const q = `INSERT INTO device_bindings (user_id, name, device_fingerprint, last_ip) VALUES ($1, $2, $3, $4) RETURNING *`;
  const r = await pool.query(q, [userId, name || null, fingerprint ? JSON.stringify(fingerprint) : null, ip || null]);
  return r.rows[0];
}

export async function getDevice(id: string) {
  const r = await pool.query(`SELECT * FROM device_bindings WHERE id = $1`, [id]);
  return r.rows[0] || null;
}

export async function revokeDevice(id: string) {
  await pool.query(`UPDATE device_bindings SET revoked = true WHERE id = $1`, [id]);
}
