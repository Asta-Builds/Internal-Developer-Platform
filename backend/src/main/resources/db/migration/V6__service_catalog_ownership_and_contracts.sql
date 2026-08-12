-- Flyway Migration V6: Service catalog ownership, dependency mapping and API contracts.
--
-- Closes the Step-1 catalog gaps from the test-lead checklist:
--   1. every service gets a contact channel and a technical documentation link
--   2. dependencies now cover external APIs, databases and queues, with an
--      explicit direction: DOWNSTREAM means the source calls/consumes the target
--   3. services without registered endpoints (srv-backstage) get their contracts
ALTER TABLE services ADD COLUMN contact_channel VARCHAR(255);
ALTER TABLE services ADD COLUMN docs_url VARCHAR(500);
ALTER TABLE services ADD COLUMN grafana_url VARCHAR(500);
ALTER TABLE services ADD COLUMN slo_availability VARCHAR(100);
ALTER TABLE services ADD COLUMN slo_latency_p95 VARCHAR(100);
ALTER TABLE services ADD COLUMN escalation_policy VARCHAR(255);
ALTER TABLE services ADD COLUMN scorecard_grade VARCHAR(32);

UPDATE services SET
  contact_channel = '#pay-core',
  docs_url = 'https://techdocs.company.internal/payment-gateway',
  grafana_url = 'https://grafana.company.internal/d/service-overview?var-service=srv-payment',
  slo_availability = '99.99% Availability (Error Budget: 4.3m/mo)',
  slo_latency_p95 = '< 50ms p95, < 120ms p99',
  escalation_policy = 'PagerDuty Tier-1 On-Call (P1 SLA: 5min, Secondary: #pay-oncall)',
  scorecard_grade = 'GOLD'
WHERE id = 'srv-payment';

UPDATE services SET
  contact_channel = '#catalog-core',
  docs_url = 'https://techdocs.company.internal/product-catalog',
  grafana_url = 'https://grafana.company.internal/d/service-overview?var-service=srv-catalog',
  slo_availability = '99.95% Availability (Error Budget: 21.6m/mo)',
  slo_latency_p95 = '< 30ms p95, < 80ms p99',
  escalation_policy = 'Opsgenie Tier-2 On-Call (P1 SLA: 15min, Secondary: #catalog-core)',
  scorecard_grade = 'GOLD'
WHERE id = 'srv-catalog';

UPDATE services SET
  contact_channel = '#notifications-core',
  docs_url = 'https://techdocs.company.internal/notification-dispatcher',
  grafana_url = 'https://grafana.company.internal/d/service-overview?var-service=srv-notification',
  slo_availability = '99.90% Availability (Error Budget: 43.2m/mo)',
  slo_latency_p95 = '< 150ms p95, < 300ms p99',
  escalation_policy = 'Slack On-Call Alerting (#notifications-core, P1 SLA: 15min)',
  scorecard_grade = 'GOLD'
WHERE id = 'srv-notification';

UPDATE services SET
  contact_channel = '#core-banking',
  docs_url = 'https://techdocs.company.internal/petclinic',
  grafana_url = 'https://grafana.company.internal/d/service-overview?var-service=srv-petclinic',
  slo_availability = '99.90% Availability',
  slo_latency_p95 = '< 100ms p95',
  escalation_policy = 'Core Banking On-Call (#core-banking)',
  scorecard_grade = 'SILVER'
WHERE id = 'srv-petclinic';

UPDATE services SET
  contact_channel = '#platform-infra',
  docs_url = 'https://techdocs.company.internal/argocd-gitops',
  grafana_url = 'https://grafana.company.internal/d/service-overview?var-service=srv-argocd',
  slo_availability = '99.99% Availability',
  slo_latency_p95 = '< 20ms p95',
  escalation_policy = 'Platform Infra SRE On-Call (#platform-infra)',
  scorecard_grade = 'GOLD'
WHERE id = 'srv-argocd';

UPDATE services SET
  contact_channel = '#platform-infra',
  docs_url = 'https://techdocs.company.internal/kubernetes-platform',
  grafana_url = 'https://grafana.company.internal/d/service-overview?var-service=srv-k8s',
  slo_availability = '99.99% Availability',
  slo_latency_p95 = '< 10ms p95',
  escalation_policy = 'Platform Infra SRE On-Call (#platform-infra)',
  scorecard_grade = 'GOLD'
WHERE id = 'srv-k8s';

UPDATE services SET
  contact_channel = '#observability',
  docs_url = 'https://techdocs.company.internal/prometheus-monitoring',
  grafana_url = 'https://grafana.company.internal/d/service-overview?var-service=srv-prometheus',
  slo_availability = '99.95% Availability',
  slo_latency_p95 = '< 50ms p95',
  escalation_policy = 'Observability Team On-Call (#observability)',
  scorecard_grade = 'GOLD'
WHERE id = 'srv-prometheus';

UPDATE services SET
  contact_channel = '#security-iam',
  docs_url = 'https://techdocs.company.internal/keycloak-iam',
  grafana_url = 'https://grafana.company.internal/d/service-overview?var-service=srv-auth',
  slo_availability = '99.99% Availability',
  slo_latency_p95 = '< 25ms p95',
  escalation_policy = 'Security & IAM SRE (#security-iam, P1 SLA: 5min)',
  scorecard_grade = 'GOLD'
WHERE id = 'srv-auth';

UPDATE services SET
  contact_channel = '#developer-experience',
  docs_url = 'https://techdocs.company.internal/backstage-portal',
  grafana_url = 'https://grafana.company.internal/d/service-overview?var-service=srv-backstage',
  slo_availability = '99.90% Availability',
  slo_latency_p95 = '< 80ms p95',
  escalation_policy = 'DevEx Team (#developer-experience)',
  scorecard_grade = 'GOLD'
WHERE id = 'srv-backstage';

UPDATE services SET
  contact_channel = '#observability',
  docs_url = 'https://techdocs.company.internal/grafana-dashboards',
  grafana_url = 'https://grafana.company.internal/d/service-overview?var-service=srv-grafana',
  slo_availability = '99.95% Availability',
  slo_latency_p95 = '< 40ms p95',
  escalation_policy = 'Observability Team (#observability)',
  scorecard_grade = 'GOLD'
WHERE id = 'srv-grafana';

-- Dependency direction semantics: DOWNSTREAM (default) = the source service
-- calls or consumes the target; UPSTREAM = the source feeds the target.
ALTER TABLE dependencies ADD COLUMN target_external VARCHAR(255);
ALTER TABLE dependencies ADD COLUMN direction VARCHAR(20) NOT NULL DEFAULT 'DOWNSTREAM';
-- External resources are not registered services, so the target becomes optional
-- and is replaced by the display name in target_external.
ALTER TABLE dependencies ALTER COLUMN target_service_id DROP NOT NULL;

-- External API integrations that were previously only free-text in descriptions.
INSERT INTO dependencies (id, source_service_id, target_service_id, type, target_external, direction) VALUES
('dep-ext-1', 'srv-payment', NULL, 'REST', 'Stripe Payments API (external)', 'DOWNSTREAM'),
('dep-ext-2', 'srv-notification', NULL, 'REST', 'Twilio SMS API (external)', 'DOWNSTREAM'),
('dep-ext-3', 'srv-notification', NULL, 'REST', 'SendGrid Email API (external)', 'DOWNSTREAM'),
('dep-ext-4', 'srv-argocd', NULL, 'REST', 'GitHub API (external)', 'DOWNSTREAM');

-- Databases: each service's primary data store becomes a first-class dependency.
INSERT INTO dependencies (id, source_service_id, target_service_id, type, target_external, direction) VALUES
('dep-db-1', 'srv-payment', NULL, 'DB', 'PostgreSQL payments_db', 'DOWNSTREAM'),
('dep-db-2', 'srv-catalog', NULL, 'DB', 'Elasticsearch catalog-index', 'DOWNSTREAM'),
('dep-db-3', 'srv-notification', NULL, 'DB', 'PostgreSQL notifications_db', 'DOWNSTREAM'),
('dep-db-4', 'srv-petclinic', NULL, 'DB', 'MySQL petclinic_db', 'DOWNSTREAM'),
('dep-db-5', 'srv-auth', NULL, 'DB', 'PostgreSQL idm_db', 'DOWNSTREAM'),
('dep-db-6', 'srv-backstage', NULL, 'DB', 'PostgreSQL backstage_db', 'DOWNSTREAM'),
('dep-db-7', 'srv-grafana', NULL, 'DB', 'PostgreSQL grafana_db', 'DOWNSTREAM'),
('dep-db-8', 'srv-k8s', NULL, 'DB', 'etcd cluster (control-plane state)', 'DOWNSTREAM'),
('dep-db-9', 'srv-prometheus', NULL, 'DB', 'Prometheus TSDB (local storage)', 'DOWNSTREAM');

-- Queues: async event pipelines consumed by the platform.
INSERT INTO dependencies (id, source_service_id, target_service_id, type, target_external, direction) VALUES
('dep-q-1', 'srv-payment', NULL, 'KAFKA', 'Kafka topic payment-events', 'DOWNSTREAM'),
('dep-q-2', 'srv-catalog', NULL, 'KAFKA', 'Kafka topic catalog-events', 'DOWNSTREAM'),
('dep-q-3', 'srv-notification', NULL, 'KAFKA', 'Kafka topic notification-events', 'DOWNSTREAM'),
('dep-q-4', 'srv-petclinic', NULL, 'KAFKA', 'Kafka topic petclinic-events', 'DOWNSTREAM');

-- API contracts missing from V2/V3: every registered service must expose at
-- least one documented endpoint.
INSERT INTO api_endpoints (id, path, method, description, service_id) VALUES
('ep-13', '/api/v1/catalog/entities', 'GET', 'List Backstage catalog entities registered in the developer portal', 'srv-backstage'),
('ep-14', '/api/v1/templates', 'GET', 'List software templates available for scaffolding new services', 'srv-backstage'),
('ep-15', '/api/v1/notifications/status/{id}', 'GET', 'Query delivery status of a previously sent notification', 'srv-notification');