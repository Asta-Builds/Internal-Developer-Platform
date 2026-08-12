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

-- 5. pgvector Knowledge Base Vector Store for IDP Copilot RAG
CREATE TABLE IF NOT EXISTS rag_documents (
    id VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    doc_type VARCHAR(64) NOT NULL,
    source_url VARCHAR(500),
    service_id VARCHAR(64) REFERENCES services(id),
    content TEXT NOT NULL,
    embedding_dimension INT DEFAULT 1536,
    indexed_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rag_docs_service ON rag_documents(service_id);
CREATE INDEX IF NOT EXISTS idx_rag_docs_type ON rag_documents(doc_type);

-- Seed initial indexed knowledge base documents
INSERT INTO rag_documents (id, title, doc_type, source_url, service_id, content, indexed_at) VALUES
('doc-1', 'Payment Gateway Integration Guide & API Specs', 'OPENAPI_SPEC', 'https://techdocs.company.internal/payment-gateway', 'srv-payment', 'Payment Gateway Service handles credit card charges, refunds, 3DS 2.0 verification, and multi-currency transactions. Key endpoints: POST /api/v1/payments/charge, POST /api/v1/payments/refund.', CURRENT_TIMESTAMP),
('doc-2', 'Product Catalog Search & Pricing Runbook', 'RUNBOOK', 'https://techdocs.company.internal/product-catalog', 'srv-catalog', 'Product Catalog Service manages product taxonomy and dynamic pricing. Backed by OpenSearch cluster. Endpoints: GET /api/v1/products, GET /api/v1/products/{id}.', CURRENT_TIMESTAMP),
('doc-3', 'Omnichannel Notification Architecture & Providers', 'ARCHITECTURE', 'https://techdocs.company.internal/notification-dispatcher', 'srv-notification', 'Notification Dispatcher sends transactional email, SMS, and WhatsApp alerts with fallback to Twilio and SendGrid.', CURRENT_TIMESTAMP),
('doc-4', 'Golden Path Scaffolding Templates Guide', 'README', 'https://techdocs.company.internal/developer-portal', 'srv-backstage', 'IDP Scaffolder generates production-ready templates in SPRING_BOOT, ANGULAR, GO, and PYTHON with automated CI/CD and K8s manifests.', CURRENT_TIMESTAMP),
('doc-5', 'Enterprise RBAC Matrix & ABAC Policy Manual', 'ADR', 'https://techdocs.company.internal/keycloak-iam', 'srv-auth', 'Two-stage authorization engine evaluating matrix RBAC roles and contextual ABAC rules (pol-001 own-team, pol-010 prod changes, pol-020 tier-1 deletion, pol-030 rollout ceiling).', CURRENT_TIMESTAMP);
