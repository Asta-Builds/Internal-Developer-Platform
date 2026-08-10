-- Flyway Migration V4: High-Performance Database Indexes, Audit Partitioning & Vector Store

-- 1. Optimized Compound Indexes for High-Concurrency Catalog Lookups
CREATE INDEX IF NOT EXISTS idx_services_team_stack ON services(owner_team, tech_stack);
CREATE INDEX IF NOT EXISTS idx_services_status ON services(status);
CREATE INDEX IF NOT EXISTS idx_endpoints_service ON api_endpoints(service_id, method);
CREATE INDEX IF NOT EXISTS idx_feature_flags_key ON feature_flags(flag_key, enabled);

-- 2. Audit Trail Indexing for Fast Querying
CREATE INDEX IF NOT EXISTS idx_audit_actor_timestamp ON audit_log(actor_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_log(action);

-- 3. Live Transaction Ledger for Real-Time AI Fraud Scoring
CREATE TABLE IF NOT EXISTS transactions (
    id VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'EUR',
    service_id VARCHAR(64) REFERENCES services(id),
    merchant_category VARCHAR(100),
    ip_address VARCHAR(45),
    device_fingerprint VARCHAR(255),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_transactions_customer ON transactions(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at DESC);

-- 4. Fraud AI Evaluation Records
CREATE TABLE IF NOT EXISTS fraud_evaluations (
    id VARCHAR(255) PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    risk_score DOUBLE PRECISION NOT NULL,
    risk_level VARCHAR(50) NOT NULL,
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    triggered_rules VARCHAR(1000),
    model_version VARCHAR(100),
    inference_latency_ms INT,
    evaluated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fraud_eval_risk ON fraud_evaluations(risk_level, blocked);
CREATE INDEX IF NOT EXISTS idx_fraud_eval_txn ON fraud_evaluations(transaction_id);
