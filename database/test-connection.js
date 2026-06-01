#!/usr/bin/env node

/**
 * Test Neon Database Connection
 * 
 * Quick script to verify database connectivity
 */

const { Client } = require('pg');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const DATABASE_URL = process.env.DATABASE_URL;

if (!DATABASE_URL) {
  console.error('❌ ERROR: DATABASE_URL not found in .env file');
  process.exit(1);
}

async function testConnection() {
  const client = new Client({
    connectionString: DATABASE_URL,
    ssl: { rejectUnauthorized: false }
  });

  try {
    console.log('🔌 Testing connection to Neon database...\n');
    await client.connect();
    
    // Test query
    const result = await client.query('SELECT NOW() as current_time, version() as pg_version;');
    
    console.log('✅ Connection successful!\n');
    console.log('📊 Database Info:');
    console.log(`   Current Time: ${result.rows[0].current_time}`);
    console.log(`   PostgreSQL: ${result.rows[0].pg_version.split(',')[0]}`);
    console.log(`   Host: ${client.host}`);
    console.log(`   Database: ${client.database}`);
    console.log(`   User: ${client.user}`);
    console.log(`   SSL: enabled\n`);
    
    // Check if PostGIS is available
    try {
      const postgisResult = await client.query('SELECT PostGIS_Version();');
      console.log(`📍 PostGIS: ${postgisResult.rows[0].postgis_version}`);
    } catch (error) {
      console.log('⚠️  PostGIS: not installed (run npm run setup to enable)');
    }
    
    // List tables
    const tablesResult = await client.query(`
      SELECT table_name 
      FROM information_schema.tables 
      WHERE table_schema = 'public' 
      AND table_type = 'BASE TABLE'
      ORDER BY table_name;
    `);
    
    if (tablesResult.rows.length > 0) {
      console.log('\n📋 Tables:');
      tablesResult.rows.forEach(row => {
        console.log(`   ✓ ${row.table_name}`);
      });
    } else {
      console.log('\n📋 No tables found (run npm run setup to create schema)');
    }
    
    console.log('\n✅ All checks passed!');
    
  } catch (error) {
    console.error('\n❌ Connection failed:', error.message);
    console.error('\nTroubleshooting:');
    console.error('1. Check your DATABASE_URL in .env file');
    console.error('2. Verify your Neon project is active');
    console.error('3. Check your internet connection');
    process.exit(1);
  } finally {
    await client.end();
  }
}

testConnection();
