import 'dotenv/config';
import fs from 'fs';
import path from 'path';
import type { PoolClient } from 'pg';
import { pool } from '../config/db';

type BootstrapMarker = {
  tableName: string;
  migrationFile: string;
};

const BOOTSTRAP_MARKERS: BootstrapMarker[] = [
  {
    tableName: 'session_reference_fingerprints',
    migrationFile: '109_phase11a_teacher_reference_fingerprints.sql'
  },
  {
    tableName: 'timetable_uploads',
    migrationFile: '110_phase3_timetable_database_foundation.sql'
  }
];

async function ensureMigrationLedger(client: PoolClient) {
  await client.query(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      filename VARCHAR(255) PRIMARY KEY,
      applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);
}

async function loadAppliedMigrations(client: PoolClient) {
  const result = await client.query<{ filename: string }>(
    `SELECT filename
       FROM schema_migrations
      ORDER BY filename`
  );
  return new Set(result.rows.map((row: { filename: string }) => row.filename));
}

async function getBootstrapMigrationFile(client: PoolClient, files: string[]) {
  for (const marker of BOOTSTRAP_MARKERS) {
    const result = await client.query<{ present: string | null }>(`SELECT to_regclass($1) AS present`, [
      `public.${marker.tableName}`
    ]);

    if (result.rows[0]?.present) {
      const index = files.indexOf(marker.migrationFile);
      if (index >= 0) {
        return files[index];
      }
    }
  }

  return null;
}

async function recordAppliedMigration(client: PoolClient, filename: string) {
  await client.query(
    `INSERT INTO schema_migrations (filename) VALUES ($1) ON CONFLICT (filename) DO NOTHING`,
    [filename]
  );
}

export async function runMigrations() {
  const migrationsDir = path.resolve(__dirname, '../../../migrations');
  if (!fs.existsSync(migrationsDir)) {
    throw new Error(`Migrations directory not found: ${migrationsDir}`);
  }

  const files = fs.readdirSync(migrationsDir).filter((f) => f.endsWith('.sql')).sort();
  const client = await pool.connect();
  try {
    await ensureMigrationLedger(client);

    const appliedMigrations = await loadAppliedMigrations(client);
    if (appliedMigrations.size === 0) {
      const bootstrapMigration = await getBootstrapMigrationFile(client, files);
      if (bootstrapMigration) {
        const bootstrapFiles = files.slice(0, files.indexOf(bootstrapMigration) + 1);
        await client.query('BEGIN');
        try {
          for (const file of bootstrapFiles) {
            await recordAppliedMigration(client, file);
          }
          await client.query('COMMIT');
        } catch (error) {
          await client.query('ROLLBACK');
          throw error;
        }
      }
    }

    const refreshedAppliedMigrations = await loadAppliedMigrations(client);
    const pendingFiles = files.filter((file) => !refreshedAppliedMigrations.has(file));

    if (pendingFiles.length === 0) {
      return;
    }

    await client.query('BEGIN');
    for (const file of pendingFiles) {
      const sql = fs.readFileSync(path.join(migrationsDir, file), 'utf8');
      console.log('Applying migration', file);
      await client.query(sql);
      await recordAppliedMigration(client, file);
    }
    await client.query('COMMIT');
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

if (require.main === module) {
  runMigrations()
    .then(() => {
      console.log('Migrations applied');
      process.exit(0);
    })
    .catch((err) => {
      console.error('Migration failed', err);
      process.exit(1);
    });
}
