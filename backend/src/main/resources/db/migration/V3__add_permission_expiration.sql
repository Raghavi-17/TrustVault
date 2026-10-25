-- ========================================================
-- TRUSTVAULT DATABASE SCHEMA
-- Migration: V3__add_permission_expiration.sql
-- Add optional expiration timestamp to document_permissions
-- ========================================================

ALTER TABLE document_permissions ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_perm_expires ON document_permissions(expires_at);
