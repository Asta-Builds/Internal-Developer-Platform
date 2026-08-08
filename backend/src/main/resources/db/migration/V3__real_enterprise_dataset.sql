-- Flyway Migration V3: Real Enterprise Dataset for Internal Developer Platform (IDP)

-- 1. Insert Enterprise Users
INSERT INTO users (id, username, email, role) VALUES 
('usr-4', 'charlie', 'charlie@company.internal', 'DEVOPS_ENGINEER'),
('usr-5', 'diana', 'diana@company.internal', 'SECURITY_LEAD');

-- 2. Insert Real-World Microservices with GitHub Repositories
INSERT INTO services (id, name, description, repository_url, owner_team, status, tech_stack, health_percent, latency_ms, environment) VALUES 
('srv-petclinic', 'Spring PetClinic Core Service', 'Sample Spring Boot microservice demonstrating PetClinic domain model, JPA repositories, and REST APIs.', 'https://github.com/spring-projects/spring-petclinic', 'Core Banking Team', 'ACTIVE', 'SPRING_BOOT', 99.95, 12, 'PROD'),
('srv-argocd', 'Argo CD GitOps Engine', 'Declarative GitOps continuous delivery engine for Kubernetes cluster management and deployment synchronization.', 'https://github.com/argoproj/argo-cd', 'Platform Infrastructure', 'ACTIVE', 'GO', 99.99, 8, 'PROD'),
('srv-k8s', 'Kubernetes Control Plane API', 'Production container orchestration control plane managing container lifecycle, networking, and scaling.', 'https://github.com/kubernetes/kubernetes', 'Platform Infrastructure', 'ACTIVE', 'GO', 100.0, 5, 'PROD'),
('srv-prometheus', 'Prometheus Monitoring Server', 'Systems monitoring and alerting toolkit collecting real-time operational metrics via pull model.', 'https://github.com/prometheus/prometheus', 'Observability Team', 'ACTIVE', 'GO', 99.90, 14, 'PROD'),
('srv-auth', 'Keycloak Identity & Access Management', 'Enterprise Open Source Identity and Access Management for Single Sign-On (SSO) and OAuth2/OIDC token verification.', 'https://github.com/keycloak/keycloak', 'Security & IAM Team', 'ACTIVE', 'JAVA', 99.99, 10, 'PROD'),
('srv-backstage', 'Backstage Developer Portal', 'An open platform for building developer portals, service catalog, software templates, and tech docs.', 'https://github.com/backstage/backstage', 'Developer Experience', 'ACTIVE', 'NODE_JS', 99.85, 22, 'PROD'),
('srv-grafana', 'Grafana Visualization Platform', 'Operational dashboards and observability visualization engine querying Prometheus and Loki datasources.', 'https://github.com/grafana/grafana', 'Observability Team', 'ACTIVE', 'GO', 99.92, 16, 'PROD');

-- 3. Insert Real Inter-Service Dependencies
INSERT INTO dependencies (id, source_service_id, target_service_id, type) VALUES
('dep-3', 'srv-petclinic', 'srv-auth', 'REST'),
('dep-4', 'srv-auth', 'srv-notification', 'REST'),
('dep-5', 'srv-argocd', 'srv-k8s', 'KUBERNETES_API'),
('dep-6', 'srv-prometheus', 'srv-k8s', 'METRICS'),
('dep-7', 'srv-grafana', 'srv-prometheus', 'REST'),
('dep-8', 'srv-backstage', 'srv-argocd', 'REST'),
('dep-9', 'srv-petclinic', 'srv-notification', 'REST');

-- 4. Insert OpenAPI 3.0 Endpoints
INSERT INTO api_endpoints (id, path, method, description, service_id) VALUES
('ep-5', '/api/v1/petclinic/owners', 'GET', 'OpenAPI 3.0: Retrieve paginated list of pet owners with search criteria', 'srv-petclinic'),
('ep-6', '/api/v1/petclinic/pets', 'POST', 'OpenAPI 3.0: Register new pet record in clinic repository', 'srv-petclinic'),
('ep-7', '/actuator/prometheus', 'GET', 'OpenAPI 3.0 / Actuator: Prometheus metrics scrape endpoint for application telemetry', 'srv-prometheus'),
('ep-8', '/api/v1/auth/login', 'POST', 'OpenAPI 3.0: Authenticate user credentials and issue OAuth2 Bearer token', 'srv-auth'),
('ep-9', '/api/v1/auth/token/refresh', 'POST', 'OpenAPI 3.0: Refresh expired OAuth2/OIDC authentication token', 'srv-auth'),
('ep-10', '/api/v1/applications/{name}', 'GET', 'OpenAPI 3.0: Fetch Argo CD GitOps application synchronization status', 'srv-argocd'),
('ep-11', '/api/v1/namespaces/{ns}/pods', 'GET', 'OpenAPI 3.0: List Kubernetes pod instances within target namespace', 'srv-k8s'),
('ep-12', '/api/v1/dashboards', 'GET', 'OpenAPI 3.0: Query available operational Grafana dashboards', 'srv-grafana');

-- 5. Insert Enterprise Feature Flags
INSERT INTO feature_flags (id, flag_key, description, enabled, rollout_percent, service_id, target_team, updated_at) VALUES
('ff-4', 'KUBERNETES_AUTOSCALING_V2', 'Enable KEDA and HorizontalPodAutoscaler v2 for microservice workloads', TRUE, 80, 'srv-k8s', 'Platform Infrastructure', CURRENT_TIMESTAMP),
('ff-5', 'SPRING_BOOT_3_2_UPGRADE', 'Upgrade core Spring Boot microservices to 3.2.x with Java 21 Virtual Threads', TRUE, 50, 'srv-petclinic', 'Core Banking Team', CURRENT_TIMESTAMP),
('ff-6', 'OAUTH2_KEYCLOAK_SSO', 'Enforce Keycloak OAuth2 / OIDC Single Sign-On across all platform services', TRUE, 100, 'srv-auth', 'Security & IAM Team', CURRENT_TIMESTAMP),
('ff-7', 'PROMETHEUS_REMOTE_WRITE_HA', 'Enable high-availability remote write streaming for Prometheus metrics aggregator', FALSE, 0, 'srv-prometheus', 'Observability Team', CURRENT_TIMESTAMP);

-- 6. Insert Enterprise Projects & Scaffolding Jobs
INSERT INTO projects (id, name, description, stack_template, owner_team, repository_name, repository_url, status, created_at) VALUES
('prj-1', 'Spring PetClinic Portal', 'Enterprise PetClinic reference microservice architecture', 'SPRING_BOOT', 'Core Banking Team', 'spring-petclinic', 'https://github.com/spring-projects/spring-petclinic', 'ACTIVE', CURRENT_TIMESTAMP),
('prj-2', 'ArgoCD GitOps Cluster Operator', 'Declarative GitOps Kubernetes cluster controller setup', 'GO', 'Platform Infrastructure', 'argo-cd', 'https://github.com/argoproj/argo-cd', 'ACTIVE', CURRENT_TIMESTAMP);

INSERT INTO scaffold_jobs (id, project_id, status, progress_percent, current_step, step_logs, started_at, completed_at) VALUES
('job-1', 'prj-1', 'COMPLETED', 100, 'DEPLOYMENT_VERIFIED', 'Spring Boot 3.2 microservice scaffolded with GitHub Actions CI/CD and Helm charts.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('job-2', 'prj-2', 'COMPLETED', 100, 'GITOPS_SYNCED', 'ArgoCD application manifests generated and pushed to GitHub repository.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 7. Insert Security Audit Log Entries with Cryptographic HMAC-SHA256 Signatures
INSERT INTO audit_log (id, actor_id, action, target_id, details, timestamp) VALUES
('aud-4', 'usr-1 (admin)', 'SECURITY_AUDIT_ACCESS_GRANTED', 'srv-petclinic', '[HMAC-SHA256: 8f7e3d2a1b9c0e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f] Verified RBAC authorization and Keycloak token verification.', CURRENT_TIMESTAMP),
('aud-5', 'usr-2 (alice)', 'FEATURE_FLAG_ROLLOUT_UPDATED', 'KUBERNETES_AUTOSCALING_V2', '[HMAC-SHA256: 7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b7c6d5e4f3a2b1c0d9e8f7a6b] Updated canary rollout percentage for K8s Autoscaling v2 to 80%.', CURRENT_TIMESTAMP),
('aud-6', 'usr-1 (admin)', 'SECURITY_POLICY_ENFORCED', 'OAUTH2_KEYCLOAK_SSO', '[HMAC-SHA256: 9b8a7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b] Enforced mandatory Keycloak OIDC SSO authentication across gateway.', CURRENT_TIMESTAMP),
('aud-7', 'usr-4 (charlie)', 'MICROSERVICE_DEPLOYED', 'srv-argocd', '[HMAC-SHA256: 1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b] Deployed Argo CD v2.10 GitOps engine to production cluster.', CURRENT_TIMESTAMP),
('aud-8', 'usr-2 (alice)', 'SPRING_FRAMEWORK_UPGRADE', 'SPRING_BOOT_3_2_UPGRADE', '[HMAC-SHA256: 3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d] Initiated Spring Boot 3.2 upgrade campaign for core services.', CURRENT_TIMESTAMP),
('aud-9', 'usr-5 (diana)', 'PROMETHEUS_METRICS_CONFIGURED', 'srv-prometheus', '[HMAC-SHA256: 5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f] Configured security alert rules and prometheus metrics scrapers.', CURRENT_TIMESTAMP);
