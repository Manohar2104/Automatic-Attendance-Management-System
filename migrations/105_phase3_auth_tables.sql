-- Phase 3 auth tables: device_bindings, refresh_tokens, roles+permissions, account_locks
-- Idempotent

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Users (required for FK references)
CREATE TABLE IF NOT EXISTS users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email text UNIQUE NOT NULL,
  password_hash text NOT NULL,
  full_name text,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Device bindings
CREATE TABLE IF NOT EXISTS device_bindings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name text,
  device_fingerprint jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz,
  last_ip inet,
  revoked boolean NOT NULL DEFAULT false
);
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'device_bindings'
          AND column_name = 'user_id'
    ) THEN
        CREATE INDEX IF NOT EXISTS device_bindings_user_idx
            ON device_bindings(user_id);
    ELSE
        RAISE NOTICE 'Skipped device_bindings_user_idx because user_id does not exist';
    END IF;
END $$;

-- Refresh tokens (store token hash only)
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash text NOT NULL,
  device_id uuid NULL REFERENCES device_bindings(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz,
  revoked boolean NOT NULL DEFAULT false,
  expires_at timestamptz NOT NULL,
  replaced_by uuid NULL
);
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'refresh_tokens'
          AND column_name = 'user_id'
    ) THEN
        CREATE INDEX IF NOT EXISTS refresh_tokens_user_idx
            ON refresh_tokens(user_id);
    ELSE
        RAISE NOTICE 'Skipped refresh_tokens_user_idx because user_id does not exist';
    END IF;
END $$;
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'refresh_tokens'
          AND column_name = 'device_id'
    ) THEN
        CREATE INDEX IF NOT EXISTS refresh_tokens_device_idx
            ON refresh_tokens(device_id);
    ELSE
        RAISE NOTICE 'Skipped refresh_tokens_device_idx because device_id does not exist';
    END IF;
END $$;

-- RBAC tables
CREATE TABLE IF NOT EXISTS roles (
  id serial PRIMARY KEY,
  name text UNIQUE NOT NULL
);
CREATE TABLE IF NOT EXISTS permissions (
  id serial PRIMARY KEY,
  name text UNIQUE NOT NULL
);
CREATE TABLE IF NOT EXISTS role_permissions (
  role_id int REFERENCES roles(id) ON DELETE CASCADE,
  permission_id int REFERENCES permissions(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id)
);
CREATE TABLE IF NOT EXISTS user_roles (
  user_id uuid NOT NULL,
  role_id int REFERENCES roles(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, role_id)
);

-- Account lockouts
CREATE TABLE IF NOT EXISTS account_locks (
  user_id uuid PRIMARY KEY,
  failed_count int NOT NULL DEFAULT 0,
  locked_until timestamptz NULL,
  last_failed_at timestamptz NULL
);

-- Security events (audit table for token theft, suspicious activity)
CREATE TABLE IF NOT EXISTS security_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NULL,
  event_type text NOT NULL,
  event_data jsonb NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
