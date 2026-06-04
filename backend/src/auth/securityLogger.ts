import { pool } from '../config/db';

export async function logSecurityEvent(userId: string | null, eventType: string, data: any = null) {
  await pool.query(`INSERT INTO security_events (user_id, event_type, event_data) VALUES ($1, $2, $3)`, [userId, eventType, data ? JSON.stringify(data) : null]);
}
