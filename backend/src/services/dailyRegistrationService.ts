import { pool } from '../config/db';
import type { PoolClient } from 'pg';
import { validateRegisteredDevice } from '../auth/deviceService';

type DailyRegistrationInput = {
  studentId: string;
  mode?: string;
};

type DeviceBindingRow = {
  id: string;
  device_fingerprint: unknown;
};

type StudentRow = {
  id: string;
};

type DailyRegistrationRow = {
  id: string;
  student_id: string;
  academic_date: string;
  registered_at: string;
  device_binding_id: string;
  created_at?: string;
  updated_at?: string | null;
};

type DailyRegistrationRecord = {
  registrationId: string;
  studentId: string;
  academicDate: string;
  registeredAt: string;
  deviceBindingId: string;
  registered: true;
};

type DailyRegistrationStatus = {
  academicDate: string;
  registrationId: string;
  registered: true;
};

const DEFAULT_ACADEMIC_TIME_ZONE = process.env.ACADEMIC_TIME_ZONE || 'Asia/Kolkata';

function makeError(message: string, statusCode: number) {
  const error = new Error(message) as Error & { statusCode?: number };
  error.statusCode = statusCode;
  return error;
}

function trim(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

function formatAcademicDate(now = new Date(), timeZone = DEFAULT_ACADEMIC_TIME_ZONE) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(now);

  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  const year = values.year;
  const month = values.month;
  const day = values.day;

  if (!year || !month || !day) {
    throw makeError('INVALID_ACADEMIC_DATE', 400);
  }

  return `${year}-${month}-${day}`;
}

export function getCurrentAcademicDate(now = new Date(), timeZone = DEFAULT_ACADEMIC_TIME_ZONE) {
  return formatAcademicDate(now, timeZone);
}

function rowToRecord(row: DailyRegistrationRow): DailyRegistrationRecord {
  return {
    registrationId: row.id,
    studentId: row.student_id,
    academicDate: row.academic_date,
    registeredAt: row.registered_at,
    deviceBindingId: row.device_binding_id,
    registered: true
  };
}

function rowToStatus(row: DailyRegistrationRow): DailyRegistrationStatus {
  return {
    academicDate: row.academic_date,
    registrationId: row.id,
    registered: true
  };
}

async function loadStudent(studentId: string) {
  const result = await pool.query<StudentRow>(`SELECT id FROM students WHERE id = $1 LIMIT 1`, [studentId]);
  return result.rowCount ? result.rows[0] : null;
}

async function loadActiveDeviceBindings(studentId: string) {
  const result = await pool.query<DeviceBindingRow>(
    `SELECT id, device_fingerprint
       FROM device_bindings
      WHERE user_id = $1
        AND revoked = false
      ORDER BY created_at DESC`,
    [studentId]
  );
  return result.rows;
}

async function loadDailyRegistration(studentId: string, academicDate: string) {
  const result = await pool.query<DailyRegistrationRow>(
    `SELECT id, student_id, academic_date::text AS academic_date, registered_at::text AS registered_at, device_binding_id, created_at::text AS created_at, updated_at::text AS updated_at
       FROM daily_student_registrations
      WHERE student_id = $1
        AND academic_date = $2::date
      LIMIT 1`,
    [studentId, academicDate]
  );
  return result.rowCount ? result.rows[0] : null;
}

export async function registerDailyRegistration(input: DailyRegistrationInput) {
  const studentId = trim(input.studentId);
  const academicDate = formatAcademicDate();
  const mode = trim(input.mode) || 'AUTO';

  if (!studentId) {
    throw makeError('MISSING_REQUIRED_FIELDS', 400);
  }

  const student = await loadStudent(studentId);
  if (!student) {
    throw makeError('STUDENT_NOT_FOUND', 404);
  }

  const activeBinding = await validateRegisteredDevice(studentId);
  if (!activeBinding) {
    throw makeError('NO_DEVICE_BINDING', 403);
  }

  const existingRegistration = await loadDailyRegistration(studentId, academicDate);
  if (existingRegistration) {
    throw makeError('DAILY_REGISTRATION_ALREADY_EXISTS', 409);
  }

  const result = await pool.query<DailyRegistrationRow>(
    `INSERT INTO daily_student_registrations (
       student_id,
       academic_date,
       device_binding_id,
       created_at,
       updated_at
     ) VALUES ($1, $2::date, $3, now(), now())
     RETURNING id, student_id, academic_date::text AS academic_date, registered_at::text AS registered_at, device_binding_id, created_at::text AS created_at, updated_at::text AS updated_at`,
    [studentId, academicDate, activeBinding.id]
  );

  if (result.rowCount === 0) {
    throw makeError('DAILY_REGISTRATION_ALREADY_EXISTS', 409);
  }

  return rowToRecord(result.rows[0]);
}

export async function getTodayDailyRegistration(studentId: string) {
  const trimmedStudentId = trim(studentId);
  if (!trimmedStudentId) {
    throw makeError('MISSING_REQUIRED_FIELDS', 400);
  }

  const academicDate = formatAcademicDate();
  const registration = await loadDailyRegistration(trimmedStudentId, academicDate);
  return registration ? rowToRecord(registration) : null;
}

export async function getDailyRegistrationStatus(studentId: string) {
  const trimmedStudentId = trim(studentId);
  if (!trimmedStudentId) {
    throw makeError('MISSING_REQUIRED_FIELDS', 400);
  }

  const academicDate = formatAcademicDate();
  const registration = await loadDailyRegistration(trimmedStudentId, academicDate);
  return registration ? rowToStatus(registration) : null;
}

export function getDailyRegistrationErrorStatus(error: unknown) {
  return (error as { statusCode?: number })?.statusCode || 500;
}

export function getDailyRegistrationErrorMessage(error: unknown) {
  return (error as Error)?.message || 'internal_error';
}