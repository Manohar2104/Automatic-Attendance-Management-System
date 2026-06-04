import { pool } from '../config/db';

export async function userRoles(userId: string) {
  const r = await pool.query(`SELECT r.name FROM roles r JOIN user_roles ur ON ur.role_id = r.id WHERE ur.user_id = $1`, [userId]);
  return r.rows.map((x) => x.name);
}

export async function hasPermission(userId: string, permission: string) {
  const q = `
    SELECT 1 FROM user_roles ur
    JOIN role_permissions rp ON rp.role_id = ur.role_id
    JOIN permissions p ON p.id = rp.permission_id
    WHERE ur.user_id = $1 AND p.name = $2 LIMIT 1
  `;
  const r = await pool.query(q, [userId, permission]);
  return (r.rowCount ?? 0) > 0;
}
