-- ========================================================
-- TRUSTVAULT DATABASE SCHEMA
-- Migration: V1__init_schema.sql
-- Compatible with PostgreSQL and H2 (PostgreSQL syntax)
-- ========================================================

-- 1. USERS TABLE
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_active ON users(is_active);

-- 2. CATEGORIES TABLE
CREATE TABLE IF NOT EXISTS categories (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. DOCUMENTS TABLE
CREATE TABLE IF NOT EXISTS documents (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category_id VARCHAR(36),
    original_filename VARCHAR(255) NOT NULL,
    sanitized_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    tags VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_documents_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_documents_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_documents_owner ON documents(owner_id);
CREATE INDEX IF NOT EXISTS idx_documents_title ON documents(title);
CREATE INDEX IF NOT EXISTS idx_documents_category ON documents(category_id);
CREATE INDEX IF NOT EXISTS idx_documents_created ON documents(created_at);

-- 4. DOCUMENT PERMISSIONS TABLE
CREATE TABLE IF NOT EXISTS document_permissions (
    id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    permission VARCHAR(20) NOT NULL,
    granted_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_perm_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_perm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_perm_granter FOREIGN KEY (granted_by) REFERENCES users(id),
    CONSTRAINT uk_document_user_perm UNIQUE (document_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_perm_doc ON document_permissions(document_id);
CREATE INDEX IF NOT EXISTS idx_perm_user ON document_permissions(user_id);

-- 5. AUDIT EVENTS TABLE (TAMPER-EVIDENT HASH CHAIN)
CREATE TABLE IF NOT EXISTS audit_events (
    id VARCHAR(36) PRIMARY KEY,
    sequence_number BIGINT NOT NULL UNIQUE,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    timestamp_epoch_ms BIGINT,
    actor_id VARCHAR(36),
    actor_username VARCHAR(50),
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50),
    target_id VARCHAR(36),
    outcome VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    correlation_id VARCHAR(64),
    details TEXT,
    previous_hash VARCHAR(64) NOT NULL,
    current_hash VARCHAR(64) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_seq ON audit_events(sequence_number);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_events(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_events(action);
CREATE INDEX IF NOT EXISTS idx_audit_target ON audit_events(target_id);
CREATE INDEX IF NOT EXISTS idx_audit_outcome ON audit_events(outcome);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_events(timestamp);
