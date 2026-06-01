#!/bin/bash

# Database Setup Script for Attendance Management System
# This script creates the database and runs all migrations

set -e  # Exit on error

# Configuration
DB_NAME="attendance_management"
DB_USER="${DB_USER:-postgres}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

echo "=== Attendance Management System - Database Setup ==="
echo ""
echo "Database: $DB_NAME"
echo "User: $DB_USER"
echo "Host: $DB_HOST"
echo "Port: $DB_PORT"
echo ""

# Check if PostgreSQL is installed
if ! command -v psql &> /dev/null; then
    echo "Error: PostgreSQL is not installed or not in PATH"
    exit 1
fi

# Check if database exists
DB_EXISTS=$(psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" 2>/dev/null || echo "")

if [ "$DB_EXISTS" = "1" ]; then
    echo "Database '$DB_NAME' already exists."
    read -p "Do you want to drop and recreate it? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "Dropping database '$DB_NAME'..."
        psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -c "DROP DATABASE $DB_NAME;"
    else
        echo "Keeping existing database. Migrations will be applied."
    fi
fi

# Create database if it doesn't exist
if [ "$DB_EXISTS" != "1" ] || [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Creating database '$DB_NAME'..."
    psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -c "CREATE DATABASE $DB_NAME;"
fi

# Enable PostGIS extension
echo "Enabling PostGIS extension..."
psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS postgis;"

# Run migrations
echo ""
echo "Running migrations..."
echo ""

MIGRATION_DIR="$(dirname "$0")/migrations"

for migration in "$MIGRATION_DIR"/*.sql; do
    # Skip rollback scripts
    if [[ "$migration" == *"_rollback.sql" ]]; then
        continue
    fi
    
    echo "Applying migration: $(basename "$migration")"
    psql -U "$DB_USER" -h "$DB_HOST" -p "$DB_PORT" -d "$DB_NAME" -f "$migration"
done

echo ""
echo "=== Database setup completed successfully ==="
echo ""
echo "To verify the setup, run:"
echo "  psql -U $DB_USER -h $DB_HOST -p $DB_PORT -d $DB_NAME -f tests/test_enrollments_table.sql"
echo ""
