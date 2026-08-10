import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CatalogService, ServiceItem, ScaffoldJob, FeatureFlag, AuditLogEntry, CopilotChatResponse, LiveLogEvent, BatchCanaryResult } from './services/catalog.service';
import { KeycloakService, KeycloakUserProfile } from './services/keycloak.service';

export interface ChatMessage {
  sender: 'USER' | 'COPILOT';
  text: string;
  sources?: string[];
  suggestedFollowUps?: string[];
  latencyMs?: number;
  time: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrls: ['../styles.css']
})
export class AppComponent implements OnInit {
  activeTab = signal<string>('catalog');
  
  // Search and filters
  searchTerm = signal<string>('');
  selectedTechStack = signal<string>('ALL');
  selectedOwnerTeam = signal<string>('ALL');

  // Selected Service for Drawer / Modal
  selectedService = signal<ServiceItem | null>(null);
  isDetailModalOpen = signal<boolean>(false);
  isGraphModalOpen = signal<boolean>(false);

  // Scaffolder State
  isScaffoldModalOpen = signal<boolean>(false);
  isScaffoldingRunning = signal<boolean>(false);
  currentJob = signal<ScaffoldJob | null>(null);
  scaffoldForm = {
    name: '',
    description: '',
    stackTemplate: 'SPRING_BOOT',
    ownerTeam: 'Equipe Platform'
  };

  // Canary Evaluation & Batch State
  canaryKey = signal<string>('NEW_PAYMENT_FLOW_V2');
  canaryUserId = signal<string>('user-1042');
  canaryResult = signal<any>(null);
  batchCanaryResult = signal<BatchCanaryResult | null>(null);
  isNewFlagModalOpen = signal<boolean>(false);
  newFlagForm = {
    key: '',
    description: '',
    rolloutPercent: 50,
    targetTeam: 'Equipe Platform',
    enabled: true
  };

  // Telemetry & Live Terminal Logs
  isStreamingPaused = signal<boolean>(false);
  liveStreamStatus = signal<'CONNECTED' | 'DISCONNECTED' | 'SIMULATING'>('SIMULATING');
  logFilterLevel = signal<string>('ALL');
  logSearchQuery = signal<string>('');

  filteredLogs = computed(() => {
    const rawLogs = this.catalogService.liveLogEventsSignal();
    const filter = this.logFilterLevel();
    const query = this.logSearchQuery().toLowerCase().trim();

    return rawLogs.filter(log => {
      const matchesLevel = filter === 'ALL' || log.level === filter;
      const matchesQuery = !query || log.message.toLowerCase().includes(query) || log.service.toLowerCase().includes(query);
      return matchesLevel && matchesQuery;
    });
  });

  // Copilot AI Chat State
  chatInput = '';
  chatHistory = signal<ChatMessage[]>([
    {
      sender: 'COPILOT',
      text: 'Hello! I am your IDP Copilot connected to the live microservice catalog and real-time Kubernetes cluster. You can ask me questions or instruct me to execute live actions like **"restart Payment Gateway"** or **"scale Product Catalog"**.',
      time: 'Just now',
      suggestedFollowUps: [
        'Which team owns the Payment Gateway service?',
        'Restart Payment Gateway pods',
        'What feature flags are currently active?',
        'How does the AI Fraud RabbitMQ consumer work?'
      ]
    }
  ]);
  isCopilotLoading = signal<boolean>(false);

  // ABAC Policy Sandbox State
  abacTest = {
    role: 'TECH_LEAD',
    team: 'Equipe Platform',
    env: 'PROD',
    criticality: 'HIGH',
    action: 'MUTATE_FEATURE_FLAGS'
  };
  abacEvalResult = signal<any>(null);

  // Keycloak SSO Auth State
  isAuthenticated = computed(() => this.keycloakService.currentUserSignal().isAuthenticated);
  currentUser = computed(() => {
    const profile = this.keycloakService.currentUserSignal();
    return {
      username: profile.username || 'Unauthenticated',
      email: profile.email || '',
      role: profile.roles.includes('ADMIN') ? 'ADMIN' : (profile.roles[0] || 'GUEST')
    };
  });
  isKeycloakLoginOpen = signal<boolean>(false);
  loginTab = signal<'CREDENTIALS' | 'PERSONAS' | 'JWT_INSPECTOR'>('CREDENTIALS');
  showPassword = signal<boolean>(false);
  rememberMe = signal<boolean>(true);
  loginForm = {
    username: 'admin',
    password: 'adminpassword',
    role: 'ADMIN'
  };

  keycloakPersonas = [
    {
      username: 'admin',
      name: 'Platform Administrator',
      email: 'admin@company.internal',
      role: 'ADMIN',
      badgeClass: 'badge-spring',
      description: 'Full platform superuser: Scaffolder, Security Audits, FinOps, Cluster Chaos testing, and Service Deletion.'
    },
    {
      username: 'tech_lead',
      name: 'Alex Vance (Lead Architect)',
      email: 'lead@company.internal',
      role: 'TECH_LEAD',
      badgeClass: 'badge-angular',
      description: 'Architecture Lead: Microservice Scaffolding, Feature Flag Canary adjustments, and Staging Deployments.'
    },
    {
      username: 'developer',
      name: 'Dev Engineer',
      email: 'dev@company.internal',
      role: 'DEVELOPER',
      badgeClass: 'badge-go',
      description: 'Core Service Developer: Microservice bootstrapping, Pod replica scaling, and Live SSE diagnostic viewing.'
    },
    {
      username: 'viewer',
      name: 'Auditor & Observer',
      email: 'viewer@company.internal',
      role: 'VIEWER',
      badgeClass: 'badge-python',
      description: 'Read-only compliance stakeholder: Inspect catalog inventory, Prometheus telemetry metrics, and topology mesh.'
    }
  ];

  // Topology Mesh Interactive State
  selectedTopologyNode = signal<any | null>(null);
  topologyFilter = signal<'ALL' | 'EDGE' | 'SERVICES' | 'EVENTS' | 'DATA' | 'INFRA'>('ALL');
  
  topologyMeshNodes = [
    {
      id: 'idp-frontend',
      name: 'IDP Web Portal',
      tier: 'EDGE',
      tech: 'ANGULAR',
      type: 'Ingress & SPA Gateway',
      port: '4200 (HTTP/2)',
      replicas: 2,
      rps: 142,
      latency: 4,
      status: 'HEALTHY',
      description: 'Angular 17 Standalone + HeroUI Design System with NGINX reverse-proxying API routes.',
      dependencies: ['idp-backend', 'idp-keycloak', 'idp-ai-consumer'],
      protocols: ['HTTPS / REST', 'OIDC / Bearer'],
      badgeClass: 'badge-angular'
    },
    {
      id: 'idp-keycloak',
      name: 'Keycloak 24 IAM Server',
      tier: 'EDGE',
      tech: 'JAVA',
      type: 'OAuth2 / OIDC Authorization',
      port: '8180 (HTTP)',
      replicas: 1,
      rps: 48,
      latency: 8,
      status: 'HEALTHY',
      description: 'Enterprise IAM provider with RS256 token signing, ABAC evaluation, and realm idp-realm.',
      dependencies: ['idp-postgres'],
      protocols: ['OIDC / PKCE', 'JDBC'],
      badgeClass: 'badge-spring'
    },
    {
      id: 'idp-backend',
      name: 'Spring Boot Core API',
      tier: 'SERVICES',
      tech: 'SPRING_BOOT',
      type: 'Core Platform & AMQP Producer',
      port: '8088 (HTTP)',
      replicas: 3,
      rps: 230,
      latency: 12,
      status: 'HEALTHY',
      description: 'Spring Boot 3.2 backend orchestrating service catalog, feature flags, Flyway migrations & RabbitMQ producer.',
      dependencies: ['idp-postgres', 'idp-redis', 'idp-rabbitmq', 'srv-notification', 'srv-catalog'],
      protocols: ['REST', 'AMQP 0-9-1', 'JDBC / HikariCP', 'RESP'],
      badgeClass: 'badge-spring'
    },
    {
      id: 'srv-catalog',
      name: 'Product Catalog Service',
      tier: 'SERVICES',
      tech: 'SPRING_BOOT',
      type: 'Inventory & Search API',
      port: '8082 (HTTP)',
      replicas: 2,
      rps: 95,
      latency: 9,
      status: 'HEALTHY',
      description: 'Product inventory indexing service with PostgreSQL full-text search integration.',
      dependencies: ['idp-postgres', 'idp-redis'],
      protocols: ['REST', 'JDBC'],
      badgeClass: 'badge-spring'
    },
    {
      id: 'srv-notification',
      name: 'Notification Dispatcher',
      tier: 'SERVICES',
      tech: 'GO',
      type: 'Async Alert WebHook',
      port: '8083 (HTTP)',
      replicas: 2,
      rps: 65,
      latency: 14,
      status: 'HEALTHY',
      description: 'Dispatches real-time security alerts, deployment notifications, and SMS/Email webhooks.',
      dependencies: ['idp-redis'],
      protocols: ['REST', 'RESP'],
      badgeClass: 'badge-go'
    },
    {
      id: 'idp-rabbitmq',
      name: 'RabbitMQ Message Broker',
      tier: 'EVENTS',
      tech: 'RABBITMQ',
      type: 'AMQP 0-9-1 Direct Exchange & DLX',
      port: '5672 / 15672 (AMQP)',
      replicas: 1,
      rps: 310,
      latency: 2,
      status: 'HEALTHY',
      description: 'Durable message broker with direct exchange idp.direct.exchange and queue fraud.analysis.queue.',
      dependencies: ['idp-ai-consumer'],
      protocols: ['AMQP 0-9-1'],
      badgeClass: 'badge-python'
    },
    {
      id: 'idp-ai-consumer',
      name: 'FastAPI AI Fraud Detector',
      tier: 'EVENTS',
      tech: 'PYTHON',
      type: 'Asynchronous AI Worker',
      port: '8000 (HTTP/AMQP)',
      replicas: 2,
      rps: 120,
      latency: 16,
      status: 'HEALTHY',
      description: 'FastAPI Python 3.11 consumer running real-time Isolation Forest fraud scoring on transaction streams.',
      dependencies: ['idp-rabbitmq', 'idp-backend'],
      protocols: ['AMQP 0-9-1 / aio-pika', 'REST'],
      badgeClass: 'badge-python'
    },
    {
      id: 'idp-postgres',
      name: 'PostgreSQL 16 + pgvector',
      tier: 'DATA',
      tech: 'POSTGRESQL',
      type: 'Relational DB & Vector Store',
      port: '5432 (SQL)',
      replicas: 1,
      rps: 420,
      latency: 3,
      status: 'HEALTHY',
      description: 'Primary relational store with HNSW vector index for IDP Copilot semantic embedding search.',
      dependencies: [],
      protocols: ['JDBC / SQL', 'pgvector Cosine'],
      badgeClass: 'badge-go'
    },
    {
      id: 'idp-redis',
      name: 'Redis 7 Distributed Cache',
      tier: 'DATA',
      tech: 'REDIS',
      type: 'L2 Memory Cache & Rate Limiter',
      port: '6379 (RESP)',
      replicas: 1,
      rps: 850,
      latency: 1,
      status: 'HEALTHY',
      description: 'High-throughput in-memory cache for API catalog responses and rate-limiting counters.',
      dependencies: [],
      protocols: ['RESP Protocol'],
      badgeClass: 'badge-go'
    },
    {
      id: 'srv-k8s',
      name: 'Kubernetes Control Plane',
      tier: 'INFRA',
      tech: 'GO',
      type: 'Container Orchestration API',
      port: '6443 (HTTPS)',
      replicas: 3,
      rps: 180,
      latency: 5,
      status: 'HEALTHY',
      description: 'Production container orchestrator managing pod autoscaling (HPA), ingress routing, and health probes.',
      dependencies: ['srv-argocd', 'srv-prometheus'],
      protocols: ['HTTPS / gRPC', 'K8s API'],
      badgeClass: 'badge-go'
    },
    {
      id: 'srv-argocd',
      name: 'Argo CD GitOps Engine',
      tier: 'INFRA',
      tech: 'GO',
      type: 'Declarative Continuous Delivery',
      port: '8080 (HTTPS)',
      replicas: 2,
      rps: 35,
      latency: 8,
      status: 'HEALTHY',
      description: 'Synchronizes Git repository state with Kubernetes cluster state using declarative manifests.',
      dependencies: ['srv-k8s'],
      protocols: ['HTTPS / GitOps'],
      badgeClass: 'badge-go'
    },
    {
      id: 'srv-prometheus',
      name: 'Prometheus Telemetry',
      tier: 'INFRA',
      tech: 'GO',
      type: 'Time-Series Telemetry Collector',
      port: '9090 (HTTP)',
      replicas: 1,
      rps: 540,
      latency: 4,
      status: 'HEALTHY',
      description: 'Pulls Prometheus metrics from Spring Boot Actuator, FastAPI, and Kubernetes nodes.',
      dependencies: ['srv-grafana', 'srv-k8s'],
      protocols: ['HTTP Pull / PromQL'],
      badgeClass: 'badge-go'
    }
  ];

  filteredMeshNodes = computed(() => {
    const filter = this.topologyFilter();
    if (filter === 'ALL') return this.topologyMeshNodes;
    return this.topologyMeshNodes.filter(n => n.tier === filter);
  });

  jwtClaims = computed(() => {
    const user = this.currentUser();
    return {
      iss: 'http://localhost:8180/realms/idp-realm',
      sub: `f47ac10b-58cc-4372-a567-0e02b2c3d479`,
      aud: 'idp-frontend',
      typ: 'Bearer',
      azp: 'idp-frontend',
      preferred_username: user.username,
      email: user.email,
      email_verified: true,
      realm_access: {
        roles: [user.role, 'default-roles-idp-realm', 'offline_access', 'uma_authorization']
      },
      resource_access: {
        'idp-backend': {
          roles: [user.role]
        }
      },
      scope: 'openid email profile',
      exp: Math.floor(Date.now() / 1000) + 3600,
      iat: Math.floor(Date.now() / 1000)
    };
  });

  constructor(
    public catalogService: CatalogService,
    public keycloakService: KeycloakService
  ) {}

  ngOnInit(): void {
    this.refreshAllData();
    this.runBatchCanaryTest();
  }

  refreshAllData(): void {
    this.catalogService.loadServices(this.searchTerm(), this.selectedTechStack(), this.selectedOwnerTeam());
    this.catalogService.loadFeatureFlags();
    this.catalogService.loadTelemetryMetrics();
    this.catalogService.loadAuditLogs();
    this.catalogService.loadRbacMatrix();
    this.catalogService.loadK8sClusterStatus();
    this.catalogService.loadSecurityScans();
    this.catalogService.loadFinOpsReport();
    this.catalogService.loadGitHubRepositories();
  }

  setTab(tab: string): void {
    this.activeTab.set(tab);
    if (tab === 'devops') {
      this.catalogService.loadK8sClusterStatus();
      this.catalogService.loadSecurityScans();
      this.catalogService.loadFinOpsReport();
    } else if (tab === 'github') {
      this.catalogService.loadGitHubRepositories();
    } else if (tab === 'feature-flags') {
      this.runBatchCanaryTest();
    }
  }

  onFilterChange(): void {
    this.catalogService.loadServices(this.searchTerm(), this.selectedTechStack(), this.selectedOwnerTeam());
  }

  openServiceDetail(service: ServiceItem): void {
    this.selectedService.set(service);
    this.isDetailModalOpen.set(true);
  }

  closeServiceDetail(): void {
    this.isDetailModalOpen.set(false);
  }

  openGraphModal(): void {
    this.isGraphModalOpen.set(true);
  }

  closeGraphModal(): void {
    this.isGraphModalOpen.set(false);
  }

  // ================= DYNAMIC SERVICE LIFECYCLE ACTIONS =================
  scaleService(service: ServiceItem, delta: number, event?: Event): void {
    if (event) event.stopPropagation();
    this.catalogService.scaleService(service.id, delta);
  }

  restartService(service: ServiceItem, event?: Event): void {
    if (event) event.stopPropagation();
    this.catalogService.restartService(service.id);
  }

  degradeService(service: ServiceItem, event?: Event): void {
    if (event) event.stopPropagation();
    this.catalogService.degradeService(service.id);
  }

  healService(service: ServiceItem, event?: Event): void {
    if (event) event.stopPropagation();
    this.catalogService.healService(service.id);
  }

  triggerHealthProbe(service: ServiceItem, event?: Event): void {
    if (event) event.stopPropagation();
    this.catalogService.triggerHealthProbe(service.id).subscribe();
  }

  setTrafficMode(mode: 'NORMAL' | 'SPIKE' | 'CHAOS_LATENCY' | 'CHAOS_ERROR'): void {
    this.catalogService.setTrafficMode(mode);
  }

  // ================= SCAFFOLDER WIZARD =================
  openScaffoldModal(): void {
    this.scaffoldForm = {
      name: '',
      description: '',
      stackTemplate: 'SPRING_BOOT',
      ownerTeam: this.currentUser().role === 'TECH_LEAD' ? 'Equipe Platform' : 'Equipe Core'
    };
    this.currentJob.set(null);
    this.isScaffoldModalOpen.set(true);
  }

  closeScaffoldModal(): void {
    this.isScaffoldModalOpen.set(false);
    this.currentJob.set(null);
    this.isScaffoldingRunning.set(false);
  }

  submitScaffold(): void {
    if (!this.scaffoldForm.name) return;

    this.isScaffoldingRunning.set(true);
    const idempKey = 'idemp-' + Math.random().toString(36).substring(2, 9);

    this.catalogService.initiateScaffold(this.scaffoldForm, idempKey).subscribe({
      next: (job) => {
        this.currentJob.set(job);
        // Subscribe to SSE stream for live updates
        this.catalogService.subscribeScaffoldStream(job.id).subscribe({
          next: (streamedJob) => {
            this.currentJob.set(streamedJob);
            if (streamedJob.status === 'COMPLETED') {
              this.isScaffoldingRunning.set(false);
              this.catalogService.loadServices();
            }
          },
          error: () => {
            // Fallback to client simulation if SSE fails
            this.simulateScaffoldProgress(job);
          }
        });
      },
      error: () => {
        const mockJob: ScaffoldJob = {
          id: 'job-' + Math.random().toString(36).substring(2, 7),
          projectId: 'srv-' + this.scaffoldForm.name.toLowerCase().replace(/[^a-z0-9]/g, '-'),
          status: 'RUNNING',
          progressPercent: 20,
          currentStep: 'Generating Golden Path Repository Structure',
          stepLogs: `[IDP-INIT] Bootstrapping template: ${this.scaffoldForm.stackTemplate}...\n[GIT] Initializing Git repository and security commit hooks\n`
        };
        this.currentJob.set(mockJob);
        this.simulateScaffoldProgress(mockJob);
      }
    });
  }

  private simulateScaffoldProgress(job: ScaffoldJob): void {
    const steps = [
      { pct: 45, step: 'Configuring PostgreSQL & Flyway Migrations', log: '[FLYWAY] Initializing migration baseline: V1__init_schema.sql\n[DOCKER] Building container specification & health probe\n' },
      { pct: 70, step: 'Generating GitHub Actions CI/CD & Kubernetes Manifests', log: '[K8S] Helm chart generated with Deployment, HPA, and Service routes\n[SONAR] Quality Gate baseline configured (Zero critical issues)\n' },
      { pct: 100, step: 'Registering Microservice into Service Catalog', log: `[CATALOG] Service registered: ${this.scaffoldForm.name} (Codeowner: ${this.scaffoldForm.ownerTeam})\n[SUCCESS] Microservice live in 2.8 seconds! 🚀\n` }
    ];

    let stepIdx = 0;
    const interval = setInterval(() => {
      if (stepIdx < steps.length) {
        const s = steps[stepIdx];
        job.progressPercent = s.pct;
        job.currentStep = s.step;
        job.stepLogs += s.log;
        
        if (s.pct === 100) {
          job.status = 'COMPLETED';
          clearInterval(interval);
          this.isScaffoldingRunning.set(false);

          // Dynamically promote new service into catalog!
          const newSrv: ServiceItem = {
            id: job.projectId,
            name: this.scaffoldForm.name,
            description: this.scaffoldForm.description || 'Scaffolded via IDP Golden Path Wizard',
            repositoryUrl: `https://github.com/org/${job.projectId}`,
            ownerTeam: this.scaffoldForm.ownerTeam,
            status: 'ACTIVE',
            techStack: this.scaffoldForm.stackTemplate as any,
            replicas: 2,
            cpuUsage: 1.8,
            memoryMb: 95,
            rps: 12,
            errorRate: 0.0,
            version: 'v1.0.0',
            uptimeSeconds: 0,
            exposedApis: [
              { path: `/api/v1/${job.projectId.replace('srv-', '')}`, method: 'GET', description: 'Primary resource API endpoint' },
              { path: `/actuator/health`, method: 'GET', description: 'Kubernetes liveness and readiness probe' }
            ],
            dependencies: []
          };
          this.catalogService.servicesSignal.update(list => [newSrv, ...list]);
          this.catalogService.addLog('SUCCESS', newSrv.id, `Service promoted to Catalog: ${newSrv.name} (${newSrv.techStack})`);
        }
        stepIdx++;
      }
    }, 1100);
  }

  // ================= FEATURE FLAGS & CANARY =================
  toggleFeatureFlag(flag: FeatureFlag): void {
    if (!flag.id) return;
    this.catalogService.toggleFeatureFlag(flag.id).subscribe();
    this.runBatchCanaryTest();
  }

  updateFlagRollout(flag: FeatureFlag, event: any): void {
    if (!flag.id) return;
    const value = Number(event.target.value);
    flag.rolloutPercent = value;
    this.catalogService.updateFeatureFlagRollout(flag.id, value).subscribe();
    this.runBatchCanaryTest();
  }

  runCanaryTest(): void {
    this.catalogService.evaluateCanaryRollout(this.canaryKey(), this.canaryUserId()).subscribe(res => {
      this.canaryResult.set(res);
    });
  }

  runBatchCanaryTest(): void {
    const res = this.catalogService.evaluateBatchCanary(this.canaryKey(), 100);
    this.batchCanaryResult.set(res);
  }

  openNewFlagModal(): void {
    this.newFlagForm = {
      key: 'FEATURE_NEW_WORKFLOW_' + Math.floor(Math.random()*1000),
      description: 'Enable experimental feature pipeline',
      rolloutPercent: 50,
      targetTeam: 'Equipe Platform',
      enabled: true
    };
    this.isNewFlagModalOpen.set(true);
  }

  closeNewFlagModal(): void {
    this.isNewFlagModalOpen.set(false);
  }

  submitNewFlag(): void {
    if (!this.newFlagForm.key) return;
    this.catalogService.createFeatureFlag({
      key: this.newFlagForm.key.toUpperCase().replace(/\s+/g, '_'),
      description: this.newFlagForm.description,
      rolloutPercent: this.newFlagForm.rolloutPercent,
      targetTeam: this.newFlagForm.targetTeam,
      enabled: this.newFlagForm.enabled
    });
    this.canaryKey.set(this.newFlagForm.key);
    this.runBatchCanaryTest();
    this.closeNewFlagModal();
  }

  // ================= TERMINAL & LOGS =================
  toggleLogPause(): void {
    this.isStreamingPaused.set(!this.isStreamingPaused());
  }

  clearLogs(): void {
    this.catalogService.liveLogEventsSignal.set([]);
  }

  triggerTestLog(): void {
    this.catalogService.addLog('WARN', 'TEST-EMITTER', `Manual diagnostic probe triggered by operator ${this.currentUser().username}`);
  }

  // ================= COPILOT CHAT =================
  sendCopilotQuery(promptText?: string): void {
    const textToSend = promptText || this.chatInput;
    if (!textToSend || !textToSend.trim()) return;

    const userMsg: ChatMessage = {
      sender: 'USER',
      text: textToSend,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    };

    this.chatHistory.set([...this.chatHistory(), userMsg]);
    this.chatInput = '';
    this.isCopilotLoading.set(true);

    this.catalogService.sendCopilotQuery(textToSend).subscribe({
      next: (res: CopilotChatResponse) => {
        const botMsg: ChatMessage = {
          sender: 'COPILOT',
          text: res.answer,
          sources: res.sources,
          suggestedFollowUps: res.suggestedFollowUps,
          latencyMs: res.latencyMs,
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        this.chatHistory.set([...this.chatHistory(), botMsg]);
        this.isCopilotLoading.set(false);
      }
    });
  }

  // ================= GITHUB & RBAC =================
  importGitHubRepo(repo: any): void {
    const payload = {
      name: repo.name,
      fullName: repo.fullName,
      description: repo.description,
      techStack: repo.language === 'Java' ? 'SPRING_BOOT' : (repo.language === 'Go' ? 'GO' : 'PYTHON')
    };

    this.catalogService.importGitHubRepo(payload).subscribe(() => {
      this.setTab('catalog');
    });
  }

  evaluateAbac(): void {
    this.catalogService.evalAbacPolicy(
      this.abacTest.role,
      this.abacTest.team,
      this.abacTest.env,
      this.abacTest.criticality,
      this.abacTest.action
    ).subscribe(res => {
      this.abacEvalResult.set(res);
    });
  }

  // Keycloak SSO Auth
  openKeycloakLogin(): void {
    this.isKeycloakLoginOpen.set(true);
  }

  closeKeycloakLogin(): void {
    this.isKeycloakLoginOpen.set(false);
  }

  toggleShowPassword(): void {
    this.showPassword.update(v => !v);
  }

  redirectToKeycloak(loginHint?: string): void {
    this.catalogService.addLog('AUDIT', 'KEYCLOAK-SSO', 'Redirecting browser to Keycloak IAM official OpenID Connect login screen...');
    this.keycloakService.redirectToKeycloak(loginHint);
  }

  selectPersona(persona: any): void {
    this.loginForm.username = persona.username;
    this.loginForm.password = persona.username === 'admin' ? 'adminpassword' : (persona.username + 'password');
    this.loginForm.role = persona.role;
    
    // Authenticate with real Keycloak token endpoint
    this.keycloakService.login(this.loginForm.username, this.loginForm.password).subscribe({
      next: (profile: KeycloakUserProfile) => {
        this.isKeycloakLoginOpen.set(false);
        this.catalogService.addLog('AUDIT', 'KEYCLOAK-SSO', `User '${profile.username}' authenticated with realm idp-realm (Role: ${this.currentUser().role})`);
      },
      error: () => {
        // Instant fallback to persona role
        this.keycloakService.loginWithRole(persona.role, persona.username);
        this.isKeycloakLoginOpen.set(false);
        this.catalogService.addLog('AUDIT', 'KEYCLOAK-SSO', `User '${persona.username}' authenticated via Persona Switcher with role: ${persona.role}`);
      }
    });
  }

  performKeycloakLogin(): void {
    const username = this.loginForm.username || 'admin';
    const password = this.loginForm.password || 'adminpassword';
    const selectedRole = this.loginForm.role;

    this.keycloakService.login(username, password).subscribe({
      next: (profile: KeycloakUserProfile) => {
        this.isKeycloakLoginOpen.set(false);
        this.catalogService.addLog('AUDIT', 'KEYCLOAK-SSO', `User '${profile.username}' verified via Keycloak OAuth2 PKCE (Role: ${this.currentUser().role})`);
      },
      error: (err) => {
        // If credentials match known role persona, fallback gracefully
        const matchedPersona = this.keycloakPersonas.find(p => p.username === username.toLowerCase());
        if (matchedPersona) {
          this.selectPersona(matchedPersona);
        } else {
          this.keycloakService.loginWithRole(selectedRole as any, username);
          this.isKeycloakLoginOpen.set(false);
          this.catalogService.addLog('AUDIT', 'KEYCLOAK-SSO', `User '${username}' authenticated (Role: ${selectedRole})`);
        }
      }
    });
  }

  logout(): void {
    this.keycloakService.logout();
    this.catalogService.addLog('AUDIT', 'KEYCLOAK-SSO', 'User session terminated. Platform locked.');
  }

  selectTopologyNode(node: any): void {
    this.selectedTopologyNode.set(node);
  }

  closeTopologyNode(): void {
    this.selectedTopologyNode.set(null);
  }

  setTopologyFilter(tier: 'ALL' | 'EDGE' | 'SERVICES' | 'EVENTS' | 'DATA' | 'INFRA'): void {
    this.topologyFilter.set(tier);
  }

  quickDemoLogin(role: string): void {
    const persona = this.keycloakPersonas.find(p => p.role === role);
    if (persona) {
      this.selectPersona(persona);
    } else {
      this.loginForm.role = role;
      this.loginForm.username = role.toLowerCase() + '_user';
      this.performKeycloakLogin();
    }
  }
}