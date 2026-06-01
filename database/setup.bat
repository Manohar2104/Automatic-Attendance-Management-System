@echo off
REM Database Setup Script for Attendance Management System (Windows)
REM This script creates the database and runs all migrations

setlocal enabledelayedexpansion

REM Configuration
set DB_NAME=attendance_management
if "%DB_USER%"=="" set DB_USER=postgres
if "%DB_HOST%"=="" set DB_HOST=localhost
if "%DB_PORT%"=="" set DB_PORT=5432

echo === Attendance Management System - Database Setup ===
echo.
echo Database: %DB_NAME%
echo User: %DB_USER%
echo Host: %DB_HOST%
echo Port: %DB_PORT%
echo.

REM Check if PostgreSQL is installed
where psql >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Error: PostgreSQL is not installed or not in PATH
    exit /b 1
)

REM Check if database exists
psql -U %DB_USER% -h %DB_HOST% -p %DB_PORT% -tAc "SELECT 1 FROM pg_database WHERE datname='%DB_NAME%'" >nul 2>nul
set DB_EXISTS=%ERRORLEVEL%

if %DB_EXISTS% equ 0 (
    echo Database '%DB_NAME%' already exists.
    set /p REPLY="Do you want to drop and recreate it? (y/N): "
    if /i "!REPLY!"=="y" (
        echo Dropping database '%DB_NAME%'...
        psql -U %DB_USER% -h %DB_HOST% -p %DB_PORT% -c "DROP DATABASE %DB_NAME%;"
        set DB_EXISTS=1
    ) else (
        echo Keeping existing database. Migrations will be applied.
    )
)

REM Create database if it doesn't exist
if %DB_EXISTS% neq 0 (
    echo Creating database '%DB_NAME%'...
    psql -U %DB_USER% -h %DB_HOST% -p %DB_PORT% -c "CREATE DATABASE %DB_NAME%;"
)

REM Enable PostGIS extension
echo Enabling PostGIS extension...
psql -U %DB_USER% -h %DB_HOST% -p %DB_PORT% -d %DB_NAME% -c "CREATE EXTENSION IF NOT EXISTS postgis;"

REM Run migrations
echo.
echo Running migrations...
echo.

set MIGRATION_DIR=%~dp0migrations

for %%f in ("%MIGRATION_DIR%\*.sql") do (
    set "filename=%%~nxf"
    REM Skip rollback scripts
    echo !filename! | findstr /i "_rollback.sql" >nul
    if errorlevel 1 (
        echo Applying migration: %%~nxf
        psql -U %DB_USER% -h %DB_HOST% -p %DB_PORT% -d %DB_NAME% -f "%%f"
    )
)

echo.
echo === Database setup completed successfully ===
echo.
echo To verify the setup, run:
echo   psql -U %DB_USER% -h %DB_HOST% -p %DB_PORT% -d %DB_NAME% -f tests\test_enrollments_table.sql
echo.

endlocal
