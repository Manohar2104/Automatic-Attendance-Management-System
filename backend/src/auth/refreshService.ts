import crypto from 'crypto';
import { pool } from '../config/db';
import { logSecurityEvent } from './securityLogger';

const PEPPER = process.env.TOKEN_PEPPER || 'dev_pepper_change_me';
const REFRESH_EXPIRES_DAYS = Number(process.env.REFRESH_EXPIRES_DAYS || '30');

function tokenHash(raw: string) {
  return crypto.createHmac('sha256', PEPPER).update(raw).digest('hex');
}

export async function issueRefreshToken(userId: string, deviceId?: string) {
  const raw = crypto.randomBytes(48).toString('hex');
  const hash = tokenHash(raw);
  const expiresAt = new Date(Date.now() + REFRESH_EXPIRES_DAYS * 24 * 3600 * 1000);
  const q = `INSERT INTO refresh_tokens (user_id, token_hash, device_id, expires_at) VALUES ($1, $2, $3, $4) RETURNING id`;
  const client = await pool.connect();
  try {
    const r = await client.query(q, [userId, hash, deviceId || null, expiresAt.toISOString()]);
    return { raw, id: r.rows[0].id };
  } finally {
    client.release();
  }
}

export async function verifyRefreshToken(raw: string) {
  const hash = tokenHash(raw);
  const q = `SELECT * FROM refresh_tokens WHERE token_hash = $1`;
  const client = await pool.connect();
  try {
    const r = await client.query(q, [hash]);
    if (r.rowCount === 0) return null;
    const row = r.rows[0];
    // detect reuse of revoked or expired tokens (theft)
    const revokedOrExpired = row.revoked || new Date(row.expires_at) <= new Date();
    if (revokedOrExpired) {
      // log security event and revoke all refresh tokens for user/device
      await logSecurityEvent(row.user_id, 'refresh_token_reuse', { token_id: row.id, device_id: row.device_id });
      // revoke all tokens for user and device to contain potential theft
      await pool.query(`UPDATE refresh_tokens SET revoked = true WHERE user_id = $1`, [row.user_id]);
      return null;
    }
    return row;
  } finally {
    client.release();
  }
}

export async function revokeRefreshTokenById(id: string) {
  await pool.query(`UPDATE refresh_tokens SET revoked = true WHERE id = $1`, [id]);
}

export async function rotateRefreshToken(oldRaw: string, userId: string, deviceId?: string) {
  const existing = await verifyRefreshToken(oldRaw);
  if (!existing) return null;
  // revoke existing and issue new
  await revokeRefreshTokenById(existing.id);
  const issued = await issueRefreshToken(userId, deviceId);
  await pool.query(`UPDATE refresh_tokens SET replaced_by = $1 WHERE id = $2`, [issued.id, existing.id]);
  return issued;
}
