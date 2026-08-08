-- Flyway Migration V1: Database Schema Definition for IDP

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS services (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    repository_url VARCHAR(500),
    owner_team VARCHAR(255),
    status VARCHAR(50),
    tech_stack VARCHAR(50),
    health_percent DOUBLE PRECISION DEFAULT 99.9,
    latency_ms INT DEFAULT 14,
    environment VARCHAR(50) DEFAULT 'PROD'
);

CREATE TABLE IF NOT EXISTS dependencies (
    id VARCHAR(255) PRIMARY KEY,
    source_service_id VARCHAR(255) NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    target_service_id VARCHAR(255) NOT NULL,
    type VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS api_endpoints (
    id VARCHAR(255) PRIMARY KEY,
    path VARCHAR(255) NOT NULL,
    method VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    service_id VARCHAR(255) NOT NULL REFERENCES services(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS projects (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    stack_template VARCHAR(50),
    owner_team VARCHAR(255),
    repository_name VARCHAR(255),
    repository_url VARCHAR(255),
    status VARCHAR(50),
    created_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS scaffold_jobs (
    id VARCHAR(255) PRIMARY KEY,
    project_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    current_step VARCHAR(255),
    step_logs VARCHAR(2000),
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS feature_flags (
    id VARCHAR(255) PRIMARY KEY,
    flag_key VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    rollout_percent INT NOT NULL DEFAULT 0,
    service_id VARCHAR(255),
    target_team VARCHAR(255),
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
    id VARCHAR(255) PRIMARY KEY,
    actor_id VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL,
    target_id VARCHAR(255),
    details VARCHAR(2000),
    timestamp TIMESTAMP
);
