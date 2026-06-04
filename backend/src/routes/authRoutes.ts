import express from 'express';
import { pool } from '../config/db';
import { verifyPassword } from '../auth/passwordService';
import { signAccessToken } from '../auth/jwtService';
import { issueRefreshToken, rotateRefreshToken, revokeRefreshTokenById, verifyRefreshToken } from '../auth/refreshService';
import { createDeviceBinding } from '../auth/deviceService';
import { generateFingerprint } from '../auth/fingerprint';
import { recordFailedLogin, resetFailed, isAccountLocked } from '../auth/lockoutService';
import { authMiddleware } from '../middleware/authMiddleware';

const router = express.Router();

router.post('/login', async (req, res) => {
  const { email, password, deviceName } = req.body;
  const client = await pool.connect();
  try {
    const r = await client.query(`SELECT id, password_hash FROM users WHERE email = $1 LIMIT 1`, [email]);
    if (r.rowCount === 0) return res.status(401).json({ error: 'invalid_credentials' });
    const user = r.rows[0];
    const locked = await isAccountLocked(user.id);
    if (locked) return res.status(423).json({ error: 'account_locked' });
    const ok = await verifyPassword(password, user.password_hash);
    if (!ok) {
      await recordFailedLogin(user.id);
      return res.status(401).json({ error: 'invalid_credentials' });
    }
    // success
    await resetFailed(user.id);
    const { token: accessToken } = await signAccessToken(user.id, []);
    const fingerprint = generateFingerprint(req);
    const device = deviceName ? await createDeviceBinding(user.id, deviceName, fingerprint, req.ip) : null;
    const refresh = await issueRefreshToken(user.id, device ? device.id : undefined);
    return res.json({ accessToken, refreshToken: refresh.raw, deviceId: device ? device.id : null });
  } finally {
    client.release();
  }
});

router.post('/refresh', async (req, res) => {
  const { refreshToken } = req.body;
  if (!refreshToken) return res.status(400).json({ error: 'missing_refresh' });
  const existing = await verifyRefreshToken(refreshToken);
  if (!existing) return res.status(401).json({ error: 'invalid_refresh' });
  // rotate
  const rotated = await rotateRefreshToken(refreshToken, existing.user_id, existing.device_id);
  if (!rotated) return res.status(401).json({ error: 'invalid_refresh' });
  const { token: accessToken } = await signAccessToken(existing.user_id, []);
  return res.json({ accessToken, refreshToken: rotated.raw });
});

router.post('/logout', async (req, res) => {
  const { refreshToken } = req.body;
  if (!refreshToken) return res.status(400).json({ error: 'missing_refresh' });
  const existing = await verifyRefreshToken(refreshToken);
  if (!existing) return res.json({ loggedOut: true });
  await revokeRefreshTokenById(existing.id);
  return res.json({ loggedOut: true });
});

router.get('/me', authMiddleware, async (req, res) => {
  const user = (req as any).user;
  const r = await pool.query(`SELECT id, email, full_name FROM users WHERE id = $1`, [user.id]);
  if (r.rowCount === 0) return res.status(404).json({ error: 'not_found' });
  return res.json({ user: r.rows[0] });
});

export default router;
