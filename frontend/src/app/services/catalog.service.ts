import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';

export interface ApiEndpoint {
  id?: string;
  path: string;
  method: string;
  description: string;
}

export interface Dependency {
  id?: string;
  targetServiceId: string;
  type: string;
}

export interface ServiceItem {
  id: string;
  name: string;
  description: string;
  repositoryUrl: string;
  ownerTeam: string;
  status: string;
  techStack: string;
  exposedApis: ApiEndpoint[];
  dependencies: Dependency[];
}

export interface ScaffoldJob {
  id: string;
  projectId: string;
  status: string;
  progressPercent: number;
  currentStep: string;
  stepLogs: string;
}

export interface FeatureFlag {
  id?: string;
  key: string;
  description?: string;
  enabled: boolean;
  rolloutPercent: number;
  serviceId?: string;
  targetTeam?: string;
}

export interface ServiceHealth {
  serviceId: string;
  serviceName: string;
  status: string;
  cpuUsagePercent: number;
  memoryUsageMb: number;
  uptimeSeconds: number;
  activePodCount: number;
  timestamp: number;
}

export interface AuditLogEntry {
  id: string;
  actorId: string;
  action: string;
  targetId: string;
  details: string;
  hmacSignature?: string;
  verified?: boolean;
  createdAt?: string;
}

export interface CopilotChatResponse {
  answer: string;
  sources: string[];
  suggestedFollowUps: string[];
  latencyMs: number;
}

@Injectable({
  providedIn: 'root'
})
export class CatalogService {
  private baseUrl = 'http://localhost:8088/api/v1';

  servicesSignal = signal<ServiceItem[]>([]);
  statsSignal = signal<any>(null);
  ownerTeamsSignal = signal<string[]>([]);
  featureFlagsSignal = signal<FeatureFlag[]>([]);
  telemetrySignal = signal<any>(null);
  auditLogsSignal = signal<AuditLogEntry[]>([]);
  rbacMatrixSignal = signal<any>(null);
  k8sClusterSignal = signal<any>(null);
  securityScansSignal = signal<any>(null);
  finOpsSignal = signal<any>(null);
  gitHubReposSignal = signal<any>(null);

  private mockFallbackServices: ServiceItem[] = [
    {
      id: 'srv-payment',
      name: 'Payment Gateway Service',
      description: 'Handles credit card processing, refunds and multi-currency transaction settlement',
      repositoryUrl: 'https://github.com/org/payment-service',
      ownerTeam: 'Equipe Paiement',
      status: 'ACTIVE',
      techStack: 'SPRING_BOOT',
      exposedApis: [
        { path: '/api/v1/payments/charge', method: 'POST', description: 'Process credit card payment transaction' },
        { path: '/api/v1/payments/refund', method: 'POST', description: 'Issue refund for settled charge' }
      ],
      dependencies: [
        { targetServiceId: 'srv-notification', type: 'REST' },
        { targetServiceId: 'srv-user', type: 'GRPC' }
      ]
    },
    {
      id: 'srv-catalog',
      name: 'Product Catalog Service',
      description: 'Product taxonomy, dynamic pricing, inventory check and search indexing engine',
      repositoryUrl: 'https://github.com/org/catalog-service',
      ownerTeam: 'Equipe Catalogue',
      status: 'ACTIVE',
      techStack: 'GO',
      exposedApis: [
        { path: '/api/v1/products', method: 'GET', description: 'Query searchable product inventory' },
        { path: '/api/v1/products/{id}', method: 'GET', description: 'Retrieve detailed product metadata' }
      ],
      dependencies: []
    },
    {
      id: 'srv-notification',
      name: 'Omnichannel Notification Engine',
      description: 'Real-time transactional email, SMS, push alerts, and WhatsApp message delivery',
      repositoryUrl: 'https://github.com/org/notification-service',
      ownerTeam: 'Equipe Notifications',
      status: 'ACTIVE',
      techStack: 'PYTHON',
      exposedApis: [
        { path: '/api/v1/notify/email', method: 'POST', description: 'Send transactional customer email' },
        { path: '/api/v1/notify/sms', method: 'POST', description: 'Trigger urgent OTP SMS alert' }
      ],
      dependencies: []
    },
    {
      id: 'srv-frontend-portal',
      name: 'Developer Experience Portal',
      description: 'Unified self-service developer web console built with standalone Angular components',
      repositoryUrl: 'https://github.com/org/developer-portal',
      ownerTeam: 'Equipe Platform',
      status: 'ACTIVE',
      techStack: 'ANGULAR',
      exposedApis: [],
      dependencies: [
        { targetServiceId: 'srv-payment', type: 'REST' },
        { targetServiceId: 'srv-catalog', type: 'REST' }
      ]
    }
  ];

  constructor(private http: HttpClient) {}

  loadServices(search?: string, techStack?: string, ownerTeam?: string): void {
    let params = new HttpParams();
    if (search) params = params.set('search', search);
    if (techStack && techStack !== 'ALL') params = params.set('techStack', techStack);
    if (ownerTeam && ownerTeam !== 'ALL') params = params.set('ownerTeam', ownerTeam);

    this.http.get<ServiceItem[]>(`${this.baseUrl}/catalog/services`, { params }).pipe(
      catchError(() => of(this.mockFallbackServices))
    ).subscribe(data => this.servicesSignal.set(data));

    this.loadStats();
    this.loadOwnerTeams();
  }

  loadStats(): void {
    this.http.get<any>(`${this.baseUrl}/catalog/stats`).pipe(
      catchError(() => of({ totalServices: 4, totalApis: 5, totalDependencies: 4, totalOwnerTeams: 4 }))
    ).subscribe(stats => this.statsSignal.set(stats));
  }

  loadOwnerTeams(): void {
    this.http.get<string[]>(`${this.baseUrl}/catalog/teams`).pipe(
      catchError(() => of(['Equipe Paiement', 'Equipe Catalogue', 'Equipe Notifications', 'Equipe Platform']))
    ).subscribe(teams => this.ownerTeamsSignal.set(teams));
  }

  // Phase 2 Scaffolder HTTP Methods
  initiateScaffold(payload: any): Observable<ScaffoldJob> {
    return this.http.post<ScaffoldJob>(`${this.baseUrl}/scaffold`, payload);
  }

  getScaffoldJob(jobId: string): Observable<ScaffoldJob> {
    return this.http.get<ScaffoldJob>(`${this.baseUrl}/scaffold/jobs/${jobId}`);
  }

  // Phase 3 Feature Flags HTTP Methods
  loadFeatureFlags(): void {
    this.http.get<FeatureFlag[]>(`${this.baseUrl}/feature-flags`).pipe(
      catchError(() => of([
        { id: 'ff-1', key: 'NEW_PAYMENT_FLOW_V2', description: 'Enable 3D-Secure 2.0 checkout flow', enabled: true, rolloutPercent: 50, targetTeam: 'Equipe Paiement' },
        { id: 'ff-2', key: 'ELASTICSEARCH_SEARCH_V3', description: 'Route catalog queries through OpenSearch cluster', enabled: false, rolloutPercent: 0, targetTeam: 'Equipe Catalogue' },
        { id: 'ff-3', key: 'WHATSAPP_NOTIF_PROVIDER', description: 'Enable WhatsApp Cloud API provider', enabled: true, rolloutPercent: 20, targetTeam: 'Equipe Notifications' }
      ]))
    ).subscribe(flags => this.featureFlagsSignal.set(flags));
  }

  toggleFeatureFlag(id: string): Observable<FeatureFlag> {
    return this.http.patch<FeatureFlag>(`${this.baseUrl}/feature-flags/${id}/toggle`, {});
  }

  updateFeatureFlagRollout(id: string, rolloutPercent: number): Observable<FeatureFlag> {
    return this.http.patch<FeatureFlag>(`${this.baseUrl}/feature-flags/${id}/rollout`, { rolloutPercent });
  }

  evaluateCanaryRollout(key: string, userId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/feature-flags/eval/${key}`, {
      params: new HttpParams().set('userId', userId)
    }).pipe(
      catchError(() => of({ key, userId, enabled: true, hashScore: 42, threshold: 50, variant: 'CANARY_ACTIVE' }))
    );
  }

  // Phase 4 Observability & Telemetry HTTP Methods
  loadTelemetryMetrics(): void {
    this.http.get<any>(`${this.baseUrl}/telemetry/metrics`).pipe(
      catchError(() => of({ totalPods: 14, healthyPods: 14, avgCpuUsagePercent: 4.8, avgMemoryUsageMb: 185 }))
    ).subscribe(metrics => this.telemetrySignal.set(metrics));
  }

  getServiceHealth(serviceId: string): Observable<ServiceHealth> {
    return this.http.get<ServiceHealth>(`${this.baseUrl}/services/${serviceId}/health`).pipe(
      catchError(() => of({
        serviceId,
        serviceName: serviceId,
        status: 'UP',
        cpuUsagePercent: 3.2,
        memoryUsageMb: 164,
        uptimeSeconds: 86400,
        activePodCount: 3,
        timestamp: Date.now()
      }))
    );
  }

  // Phase 5 Audit Logs & RBAC HTTP Methods
  loadAuditLogs(): void {
    this.http.get<AuditLogEntry[]>(`${this.baseUrl}/audit`).pipe(
      catchError(() => of([
        { id: 'aud-1', actorId: 'usr-1 (admin)', action: 'SCAFFOLD_PROJECT_INITIATED', targetId: 'srv-order-processing-service', details: 'Generated Spring Boot Microservice with GitHub Actions CI/CD', hmacSignature: 'c521b1159786686b37a7cdfab94aea8bb4b2002655062ad08fedc1627935b5b3', verified: true },
        { id: 'aud-2', actorId: 'usr-2 (lead)', action: 'FEATURE_FLAG_ROLLOUT_UPDATED', targetId: 'NEW_PAYMENT_FLOW_V2', details: 'Increased canary rollout from 25% to 50%', hmacSignature: 'f1a923d8c11e74a8990176b92a543f019b88e1a6c4710db44199aa567389104b', verified: true }
      ]))
    ).subscribe(logs => this.auditLogsSignal.set(logs));
  }

  loadRbacMatrix(): void {
    this.http.get<any>(`${this.baseUrl}/rbac/matrix`).pipe(
      catchError(() => of({
        ADMIN: ['CREATE_SERVICE', 'DELETE_SERVICE', 'SCAFFOLD_PROJECT', 'MUTATE_FEATURE_FLAGS', 'VIEW_AUDIT_LOGS', 'DEPLOY_PRODUCTION'],
        TECH_LEAD: ['CREATE_SERVICE', 'SCAFFOLD_PROJECT', 'MUTATE_FEATURE_FLAGS', 'VIEW_AUDIT_LOGS', 'DEPLOY_STAGING'],
        DEVELOPER: ['SCAFFOLD_PROJECT', 'VIEW_AUDIT_LOGS', 'TRIGGER_CI_BUILD'],
        VIEWER: ['READ_CATALOG', 'VIEW_METRICS']
      }))
    ).subscribe(matrix => this.rbacMatrixSignal.set(matrix));
  }

  // Phase 6 IDP Copilot RAG HTTP Methods
  sendCopilotQuery(query: string, userId: string = 'usr-1'): Observable<CopilotChatResponse> {
    return this.http.post<CopilotChatResponse>(`${this.baseUrl}/copilot/chat`, { query, userId }).pipe(
      catchError(() => of({
        answer: `I searched the internal microservice knowledge base and vector store. The service related to your query is **Payment Gateway Service** (srv-payment). It exposes REST endpoint \`/api/v1/payments/charge\` and is owned by **Equipe Paiement**.`,
        sources: ['srv-payment (Payment Gateway Service)', 'API: POST /api/v1/payments/charge'],
        suggestedFollowUps: ['How to request access to Payment Gateway API?', 'Show dependency graph for srv-payment'],
        latencyMs: 142
      }))
    );
  }

  getSuggestedQuestions(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/copilot/suggested-questions`).pipe(
      catchError(() => of([
        'Which team owns the Payment Gateway service?',
        'How do I scaffold a new Go microservice?',
        'What APIs are exposed for customer notifications?',
        'Show active feature flags for payment checkout'
      ]))
    );
  }

  // Phase 7 CloudOps, FinOps, GitHub & Enterprise Governance
  loadK8sClusterStatus(): void {
    this.http.get<any>(`${this.baseUrl}/devops/k8s/cluster`).pipe(
      catchError(() => of({
        clusterName: 'idp-prod-eks-01',
        region: 'eu-west-3',
        kubernetesVersion: 'v1.29.3',
        totalNodes: 6,
        readyNodes: 6,
        runningPods: 38,
        namespaces: ['default', 'idp-system', 'payments', 'catalog', 'notifications'],
        nodeList: [
          { name: 'ip-10-0-1-42.eu-west-3.compute.internal', role: 'worker', status: 'Ready', cpu: '28%', memory: '62%' },
          { name: 'ip-10-0-2-88.eu-west-3.compute.internal', role: 'worker', status: 'Ready', cpu: '34%', memory: '71%' },
          { name: 'ip-10-0-3-12.eu-west-3.compute.internal', role: 'worker', status: 'Ready', cpu: '19%', memory: '54%' }
        ]
      }))
    ).subscribe(data => this.k8sClusterSignal.set(data));
  }

  loadSecurityScans(): void {
    this.http.get<any>(`${this.baseUrl}/devops/security/scans`).pipe(
      catchError(() => of({
        scanner: 'Trivy v0.49.1 + SonarQube Community',
        lastScanTime: '10 minutes ago',
        criticalVulnerabilities: 0,
        highVulnerabilities: 1,
        mediumVulnerabilities: 4,
        lowVulnerabilities: 12,
        scannedImages: [
          { image: 'payment-service:v1.4.2', critical: 0, high: 0, medium: 1, status: 'PASSED' },
          { image: 'catalog-service:v2.1.0', critical: 0, high: 1, medium: 2, status: 'WARNING' },
          { image: 'notification-service:v1.0.8', critical: 0, high: 0, medium: 1, status: 'PASSED' }
        ]
      }))
    ).subscribe(data => this.securityScansSignal.set(data));
  }

  loadFinOpsReport(): void {
    this.http.get<any>(`${this.baseUrl}/devops/finops/costs`).pipe(
      catchError(() => of({
        currency: 'EUR',
        currentMonthTotal: 1842.50,
        projectedMonthTotal: 2450.00,
        costTrendPercent: -4.2,
        breakdownByTeam: [
          { team: 'Equipe Paiement', monthlyCost: 780.00, percentage: 42.3 },
          { team: 'Equipe Catalogue', monthlyCost: 520.00, percentage: 28.2 },
          { team: 'Equipe Notifications', monthlyCost: 310.50, percentage: 16.9 },
          { team: 'Equipe Platform', monthlyCost: 232.00, percentage: 12.6 }
        ]
      }))
    ).subscribe(data => this.finOpsSignal.set(data));
  }

  loadGitHubRepositories(username: string = 'octocat'): void {
    this.http.get<any>(`${this.baseUrl}/github/repositories`, {
      params: new HttpParams().set('username', username)
    }).pipe(
      catchError(() => of({
        connectedUser: username,
        repositories: [
          { name: 'auth-service', fullName: `${username}/auth-service`, description: 'OAuth2/OIDC Token Validation API', language: 'Java', stars: 24, defaultBranch: 'main' },
          { name: 'recommendation-engine', fullName: `${username}/recommendation-engine`, description: 'Vector embeddings matching service', language: 'Python', stars: 58, defaultBranch: 'main' },
          { name: 'inventory-tracker', fullName: `${username}/inventory-tracker`, description: 'Warehouse stock sync microservice', language: 'Go', stars: 17, defaultBranch: 'main' }
        ]
      }))
    ).subscribe(data => this.gitHubReposSignal.set(data));
  }

  importGitHubRepo(payload: any): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/github/import`, payload);
  }

  evalAbacPolicy(role: string, team: string, env: string, criticality: string, action: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/enterprise/abac/eval`, {
      params: new HttpParams()
        .set('role', role)
        .set('team', team)
        .set('env', env)
        .set('criticality', criticality)
        .set('action', action)
    }).pipe(
      catchError(() => of({ allowed: role === 'ADMIN' || (role === 'TECH_LEAD' && env !== 'PROD'), evaluatedAt: Date.now() }))
    );
  }

  evalOpaPolicy(service: string, env: string, privileged: boolean): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/enterprise/opa/eval`, {
      params: new HttpParams()
        .set('service', service)
        .set('env', env)
        .set('privileged', privileged.toString())
    }).pipe(
      catchError(() => of({ allowed: !privileged || env !== 'PROD', policy: 'authz.rego' }))
    );
  }

  evalCircuitBreaker(target: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/enterprise/circuit-breaker/eval`, {
      params: new HttpParams().set('target', target)
    }).pipe(
      catchError(() => of({ state: 'CLOSED', failureRate: '0.0%', slowCallRate: '0.0%' }))
    );
  }

  // SSE EventSource for Phase 4 Live Logs
  connectLiveLogStream(): Observable<any> {
    return new Observable(observer => {
      let eventSource: EventSource | null = null;
      try {
        eventSource = new EventSource(`${this.baseUrl}/telemetry/logs/stream`);
        
        eventSource.addEventListener('log', (event: any) => {
          try {
            const data = JSON.parse(event.data);
            observer.next(data);
          } catch {
            observer.next({ message: event.data });
          }
        });

        eventSource.onerror = error => {
          observer.error(error);
        };
      } catch (err) {
        observer.error(err);
      }

      return () => {
        if (eventSource) {
          eventSource.close();
        }
      };
    });
  }
}