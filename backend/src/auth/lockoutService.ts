import { pool } from '../config/db';

const MAX_FAILED = Number(process.env.MAX_FAILED_ATTEMPTS || '5');
const INITIAL_LOCK_MINUTES = Number(process.env.INITIAL_LOCK_MINUTES || '15');

export async function recordFailedLogin(userId: string) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const r = await client.query(`SELECT failed_count FROM account_locks WHERE user_id = $1 FOR UPDATE`, [userId]);
    if (r.rowCount === 0) {
      await client.query(`INSERT INTO account_locks (user_id, failed_count, last_failed_at) VALUES ($1, 1, now())`, [userId]);
      await client.query('COMMIT');
      return { locked: false, failed: 1 };
    }
    const failed = r.rows[0].failed_count + 1;
    let lockedUntil = null;
    if (failed >= MAX_FAILED) {
      lockedUntil = new Date(Date.now() + INITIAL_LOCK_MINUTES * 60 * 1000);
      await client.query(`UPDATE account_locks SET failed_count = $1, locked_until = $2, last_failed_at = now() WHERE user_id = $3`, [failed, lockedUntil.toISOString(), userId]);
    } else {
      await client.query(`UPDATE account_locks SET failed_count = $1, last_failed_at = now() WHERE user_id = $2`, [failed, userId]);
    }
    await client.query('COMMIT');
    return { locked: !!lockedUntil, failed };
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

export async function isAccountLocked(userId: string) {
  const r = await pool.query(`SELECT locked_until FROM account_locks WHERE user_id = $1`, [userId]);
  if (r.rowCount === 0) return false;
  const lockedUntil = r.rows[0].locked_until;
  if (!lockedUntil) return false;
  return new Date(lockedUntil) > new Date();
}

export async function resetFailed(userId: string) {
  await pool.query(`DELETE FROM account_locks WHERE user_id = $1`, [userId]);
}
