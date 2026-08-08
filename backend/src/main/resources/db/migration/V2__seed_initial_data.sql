-- Flyway Migration V2: Seed Data Insertion for IDP

INSERT INTO users (id, username, email, role) VALUES 
('usr-1', 'admin', 'admin@company.internal', 'ADMIN'),
('usr-2', 'alice', 'alice@company.internal', 'TECH_LEAD'),
('usr-3', 'bob', 'bob@company.internal', 'DEVELOPER');

INSERT INTO services (id, name, description, repository_url, owner_team, status, tech_stack, health_percent, latency_ms, environment) 
VALUES ('srv-payment', 'Payment Gateway Service', 'Handles card processing, refunds and transaction settlement with stripe integration.', 'https://github.com/org/payment-service', 'Equipe Paiement', 'ACTIVE', 'SPRING_BOOT', 99.98, 12, 'PROD');

INSERT INTO services (id, name, description, repository_url, owner_team, status, tech_stack, health_percent, latency_ms, environment) 
VALUES ('srv-catalog', 'Product Catalog API', 'Manages inventory, categories, pricing search indexes and Elasticsearch sync.', 'https://github.com/org/catalog-service', 'Equipe Catalogue', 'ACTIVE', 'ANGULAR', 100.0, 8, 'PROD');

INSERT INTO services (id, name, description, repository_url, owner_team, status, tech_stack, health_percent, latency_ms, environment) 
VALUES ('srv-notification', 'Notification Dispatcher', 'Dispatches SMS, Email, and Push Notifications via Twilio and SendGrid.', 'https://github.com/org/notification-service', 'Equipe Notifications', 'ACTIVE', 'GO', 99.95, 15, 'PROD');

INSERT INTO dependencies (id, source_service_id, target_service_id, type) VALUES
('dep-1', 'srv-payment', 'srv-notification', 'REST'),
('dep-2', 'srv-catalog', 'srv-payment', 'REST');

INSERT INTO api_endpoints (id, path, method, description, service_id) VALUES
('ep-1', '/api/v1/payments/charge', 'POST', 'Process immediate credit card transaction', 'srv-payment'),
('ep-2', '/api/v1/payments/{id}/refund', 'POST', 'Initiate payment refund workflow', 'srv-payment'),
('ep-3', '/api/v1/products', 'GET', 'Retrieve paginated list of catalog products', 'srv-catalog'),
('ep-4', '/api/v1/notifications/send', 'POST', 'Send transactional email or push alert', 'srv-notification');

INSERT INTO feature_flags (id, flag_key, description, enabled, rollout_percent, service_id, target_team, updated_at) VALUES
('ff-1', 'NEW_PAYMENT_FLOW_V2', 'Enable Stripe 3D-Secure 2.0 Checkout Flow', TRUE, 50, 'srv-payment', 'Equipe Paiement', CURRENT_TIMESTAMP),
('ff-2', 'ELASTICSEARCH_SEARCH_V3', 'Elasticsearch vector similarity search', FALSE, 0, 'srv-catalog', 'Equipe Catalogue', CURRENT_TIMESTAMP),
('ff-3', 'WHATSAPP_NOTIF_PROVIDER', 'Route urgent SMS to WhatsApp Business API', TRUE, 10, 'srv-notification', 'Equipe Notifications', CURRENT_TIMESTAMP);

INSERT INTO audit_log (id, actor_id, action, target_id, details, timestamp) VALUES
('aud-1', 'usr-1 (admin)', 'SCAFFOLD_PROJECT_INITIATED', 'srv-order-processing-service', 'Generated Spring Boot Microservice with GitHub Actions CI/CD', CURRENT_TIMESTAMP),
('aud-2', 'usr-2 (alice)', 'FEATURE_FLAG_ROLLOUT_UPDATED', 'NEW_PAYMENT_FLOW_V2', 'Increased canary rollout from 50% to 75%', CURRENT_TIMESTAMP),
('aud-3', 'usr-1 (admin)', 'SERVICE_CREATED', 'srv-payment', 'Registered Payment Gateway Service into Service Catalog', CURRENT_TIMESTAMP);
