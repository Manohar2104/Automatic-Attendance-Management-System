import 'dotenv/config';
import fs from 'fs';
import path from 'path';
import { pool } from '../src/config/db';

jest.setTimeout(30000);

const hasDb = !!process.env.DATABASE_URL;

describe('Phase 3.1 timetable schema', () => {
  if (!hasDb) {
    it.skip('skipped - no DATABASE_URL', () => {});
    return;
  }

  beforeAll(async () => {
    const migrationPath = path.resolve(__dirname, '../../migrations/110_phase3_timetable_database_foundation.sql');
    const sql = fs.readFileSync(migrationPath, 'utf8');
    const client = await pool.connect();

    try {
      await client.query('BEGIN');
      await client.query(sql);
      await client.query('COMMIT');
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  });

  afterAll(async () => {
    await pool.end();
  });

  it('creates timetable foundation tables, columns, keys, constraints, and indexes', async () => {
    const tableResult = await pool.query(
      `SELECT table_name
         FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name IN ('timetable_uploads', 'timetable_entries', 'lecture_instances')`
    );

    expect(new Set(tableResult.rows.map((row: { table_name: string }) => row.table_name))).toEqual(
      new Set(['timetable_uploads', 'timetable_entries', 'lecture_instances'])
    );

    const columnResult = await pool.query(
      `SELECT table_name, column_name, is_nullable, column_default
         FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name IN ('timetable_uploads', 'timetable_entries', 'lecture_instances')`
    );

    const columns = new Map<string, { is_nullable: string; column_default: string | null }>();
    for (const row of columnResult.rows as Array<{ table_name: string; column_name: string; is_nullable: string; column_default: string | null }>) {
      columns.set(`${row.table_name}.${row.column_name}`, {
        is_nullable: row.is_nullable,
        column_default: row.column_default
      });
    }

    expect(columns.get('timetable_uploads.id')?.column_default).toContain('gen_random_uuid');
    expect(columns.get('timetable_uploads.uploaded_by')?.is_nullable).toBe('NO');
    expect(columns.get('timetable_uploads.uploaded_at')?.column_default).toContain('now');
    expect(columns.get('timetable_uploads.status')?.column_default).toContain('UPLOADED');

    expect(columns.get('timetable_entries.id')?.column_default).toContain('gen_random_uuid');
    expect(columns.get('timetable_entries.timetable_upload_id')?.is_nullable).toBe('NO');
    expect(columns.get('timetable_entries.join_window_minutes')?.column_default).toContain('5');
    expect(columns.get('timetable_entries.active_flag')?.column_default?.toLowerCase()).toContain('true');

    expect(columns.get('lecture_instances.id')?.column_default).toContain('gen_random_uuid');
    expect(columns.get('lecture_instances.session_id')?.is_nullable).toBe('YES');
    expect(columns.get('lecture_instances.activation_state')?.column_default).toContain('PENDING');
    expect(columns.get('lecture_instances.created_at')?.column_default).toContain('now');

    const keyResult = await pool.query(
      `SELECT c.relname AS table_name, pgc.contype, pgc.conname
         FROM pg_constraint pgc
         JOIN pg_class c ON c.oid = pgc.conrelid
         JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relname IN ('timetable_uploads', 'timetable_entries', 'lecture_instances')
          AND pgc.contype IN ('p', 'f', 'u', 'c')`
    );

    const constraintTypeMap: Record<string, string> = {
      p: 'PRIMARY KEY',
      f: 'FOREIGN KEY',
      u: 'UNIQUE',
      c: 'CHECK'
    };

    const constraints = new Set(
      keyResult.rows.map((row: { table_name: string; contype: string; conname: string }) =>
        `${row.table_name}:${constraintTypeMap[row.contype] ?? row.contype}:${row.conname}`
      )
    );

    expect(constraints).toEqual(
      new Set([
        'timetable_uploads:PRIMARY KEY:timetable_uploads_pkey',
        'timetable_uploads:UNIQUE:uq_timetable_uploads_checksum',
        'timetable_uploads:FOREIGN KEY:timetable_uploads_uploaded_by_fkey',
        'timetable_entries:PRIMARY KEY:timetable_entries_pkey',
        'timetable_entries:FOREIGN KEY:timetable_entries_timetable_upload_id_fkey',
        'timetable_entries:FOREIGN KEY:timetable_entries_classroom_id_fkey',
        'timetable_entries:FOREIGN KEY:timetable_entries_teacher_id_fkey',
        'timetable_entries:UNIQUE:uq_timetable_entries_row',
        'timetable_entries:CHECK:chk_timetable_entries_day_of_week',
        'timetable_entries:CHECK:chk_timetable_entries_time_order',
        'timetable_entries:CHECK:chk_timetable_entries_duration_positive',
        'timetable_entries:CHECK:chk_timetable_entries_join_window',
        'lecture_instances:PRIMARY KEY:lecture_instances_pkey',
        'lecture_instances:FOREIGN KEY:lecture_instances_timetable_entry_id_fkey',
        'lecture_instances:FOREIGN KEY:lecture_instances_session_id_fkey',
        'lecture_instances:UNIQUE:uq_lecture_instances_entry_date',
        'lecture_instances:UNIQUE:uq_lecture_instances_session_id',
        'lecture_instances:CHECK:chk_lecture_instances_time_order'
      ])
    );

    const indexResult = await pool.query(
      `SELECT indexname
         FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname IN (
            'uq_timetable_uploads_checksum',
            'idx_timetable_entries_upload_day',
            'idx_timetable_entries_classroom_teacher_day',
            'idx_lecture_instances_date_start'
          )`
    );

    expect(new Set(indexResult.rows.map((row: { indexname: string }) => row.indexname))).toEqual(
      new Set([
        'uq_timetable_uploads_checksum',
        'idx_timetable_entries_upload_day',
        'idx_timetable_entries_classroom_teacher_day',
        'idx_lecture_instances_date_start'
      ])
    );
  });
});