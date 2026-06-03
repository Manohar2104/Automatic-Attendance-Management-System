import { Pool } from 'pg';

const dbUrl = process.env.DATABASE_URL || process.env.POSTGRES_URL || 'postgres://postgres:postgres@postgres:5432/attendance';

export const pool = new Pool({ connectionString: dbUrl });

pool.on('error', (err) => {
  console.error('Unexpected PG pool error', err);
});
