# 🚀 Internal Developer Platform (IDP) - Architectural Status

Central self-service developer portal for microservice catalog management, project scaffolding, feature flag rollouts, observability streaming, RBAC audit logs, and AI-assisted documentation (IDP Copilot).

---

## 📊 Completed Phases Roadmap

- [x] **Phase 0 — Socle Technique**: Spring Boot 3.2 backend, Angular 17 Standalone frontend, PostgreSQL + `pgvector` container setup, JWT Security.
- [x] **Phase 1 — Service Catalog**: Search & filtering by tech stack and owner team, aggregate catalog statistics (`/api/catalog/stats`), service inspection modals.
- [x] **Phase 2 — Project Scaffolder MVP**: Asynchronous scaffolding engine (`/api/scaffold`), template generation (`SPRING_BOOT`, `ANGULAR`, `GO`, `PYTHON`), real-time job progress polling, automatic service promotion to catalog.
- [x] **Phase 3 — Feature Flags & Canary Release**: Feature Toggle CRUD (`/api/feature-flags`), real-time percentage rollout adjustments, hash-based Canary Release rule evaluation (`/api/feature-flags/eval/{key}?userId=...`).
- [x] **Phase 4 — Observability & SSE Telemetry**: Prometheus cluster health metrics (`/api/telemetry/metrics`), individual service health snapshots (`/api/services/{id}/health`), Server-Sent Events (SSE) live log streaming terminal (`/api/telemetry/logs/stream`).
- [x] **Phase 5 — Full Multi-Tenant RBAC & Audit Logging**: Immutable security audit records (`/api/audit`), automatic operation interceptors (`AuditService`), multi-role access control matrix (`/api/rbac/matrix`).
- [x] **Phase 6 — IDP Copilot (RAG Vector Store & Assistant IA)**: Semantic vector retrieval (`POST /api/copilot/chat`), interactive assistant with microservice source citations, starter prompt pills (`GET /api/copilot/suggested-questions`).

---

## 🛠️ Quick Start

### Running Backend (Spring Boot 3.2)
```bash
cd backend
mvn spring-boot:run
```
Runs on `http://localhost:8088` (H2 console: `http://localhost:8088/h2-console`).

### Running Frontend (Angular 17/18)
```bash
cd frontend
npm install
npm start
```
Runs on `http://localhost:4200` (or Vite dev server).

### Containerized Infrastructure
```bash
docker-compose up -d
```
Runs PostgreSQL 16 (with `pgvector`), Spring Boot API, and Angular frontend.
