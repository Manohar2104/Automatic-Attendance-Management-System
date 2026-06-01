#!/usr/bin/env node

/**
 * Neon Database Setup Script
 * 
 * This script:
 * 1. Connects to Neon database
 * 2. Enables PostGIS extension
 * 3. Runs all migrations in order
 * 4. Verifies the setup
 */

const { Client } = require('pg');
const fs = require('fs');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const DATABASE_URL = process.env.DATABASE_URL;

if (!DATABASE_URL) {
  console.error('❌ ERROR: DATABASE_URL not found in .env file');
  console.error('Please create a .env file with your Neon connection string');
  process.exit(1);
}

async function setupDatabase() {
  const client = new Client({
    connectionString: DATABASE_URL,
    ssl: { rejectUnauthorized: false }
  });

  try {
    console.log('🔌 Connecting to Neon database...');
    await client.connect();
    console.log('✅ Connected successfully!\n');

    // Enable PostGIS extension
    console.log('📍 Enabling PostGIS extension...');
    await client.query('CREATE EXTENSION IF NOT EXISTS postgis;');
    console.log('✅ PostGIS enabled\n');

    // Get list of migration files
    const migrationsDir = path.join(__dirname, 'migrations');
    const migrationFiles = fs.readdirSync(migrationsDir)
      .filter(file => file.endsWith('.sql') && !file.includes('rollback'))
      .sort();

    console.log(`📁 Found ${migrationFiles.length} migration(s)\n`);

    // Run each migration
    for (const file of migrationFiles) {
      console.log(`🔄 Running migration: ${file}`);
      const migrationPath = path.join(migrationsDir, file);
      const migrationSQL = fs.readFileSync(migrationPath, 'utf8');
      
      try {
        await client.query(migrationSQL);
        console.log(`✅ ${file} completed\n`);
      } catch (error) {
        if (error.message.includes('already exists')) {
          console.log(`⚠️  ${file} already applied (skipping)\n`);
        } else {
          throw error;
        }
      }
    }

    // Verify setup
    console.log('🔍 Verifying database setup...');
    
    const tablesResult = await client.query(`
      SELECT table_name 
      FROM information_schema.tables 
      WHERE table_schema = 'public' 
      AND table_type = 'BASE TABLE'
      ORDER BY table_name;
    `);
    
    console.log('\n📊 Tables created:');
    tablesResult.rows.forEach(row => {
      console.log(`   ✓ ${row.table_name}`);
    });

    // Check PostGIS
    const postgisResult = await client.query(`
      SELECT PostGIS_Version();
    `);
    console.log(`\n📍 PostGIS version: ${postgisResult.rows[0].postgis_version}`);

    console.log('\n🎉 Database setup completed successfully!');
    console.log('\n📝 Connection details:');
    console.log(`   Host: ${client.host}`);
    console.log(`   Database: ${client.database}`);
    console.log(`   User: ${client.user}`);
    console.log(`   SSL: enabled`);

  } catch (error) {
    console.error('\n❌ Error during setup:', error.message);
    console.error('\nFull error:', error);
    process.exit(1);
  } finally {
    await client.end();
    console.log('\n🔌 Connection closed');
  }
}

// Run setup
setupDatabase();
