-- Rollback Migration: Drop enrollments table
-- This script reverses the changes made in 001_create_enrollments_table.sql

-- Drop indexes first
DROP INDEX IF EXISTS idx_enrollments_status;
DROP INDEX IF EXISTS idx_enrollments_student_id;

-- Drop the enrollments table
DROP TABLE IF EXISTS enrollments;
