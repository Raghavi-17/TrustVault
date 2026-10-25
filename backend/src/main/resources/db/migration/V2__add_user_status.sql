-- ========================================================
-- TRUSTVAULT DATABASE SCHEMA
-- Migration: V2__add_user_status.sql
-- Add user approval status column to users table
-- ========================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
