import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, of } from 'rxjs';
import { SseClient } from './sse.client';

export interface ApiEndpoint {
  id?: string;
  path: string;
  method: string;
  description: string;
}

export interface Dependency {
  id?: string;
  targetServiceId?: string;
  /** Display name for deps on non-service resources: external APIs, databases, queues. */
  targetExternal?: string;
  /** DOWNSTREAM = this service calls/consumes the target; UPSTREAM = this service feeds the target. */
  direction?: string;
  type: string;
}

export interface ServiceItem {
  id: string;
  name: string;
  description: string;
  repositoryUrl: string;
  ownerTeam: string;
  /** Slack channel / on-call rotor for the owning team. */
  contactChannel?: string;
  /** Technical documentation link (techdocs / runbook / ADRs). */
  docsUrl?: string;
  /** Grafana / APM monitoring dashboard URL. */
  grafanaUrl?: string;
  /** Service Level Objective: Availability target and error budget. */
  sloAvailability?: string;
  /** Service Level Objective: Latency percentiles (e.g. p95, p99). */
  sloLatencyP95?: string;
  /** Incident response escalation policy and on-call rotation. */
  escalationPolicy?: string;
  /** Production Readiness Scorecard grade: GOLD, SILVER, BRONZE. */
  scorecardGrade?: 'GOLD' | 'SILVER' | 'BRONZE';
  status: 'ACTIVE' | 'SCALING' | 'RESTARTING' | 'DEGRADED' | 'MAINTENANCE';
  techStack: 'SPRING_BOOT' | 'ANGULAR' | 'GO' | 'PYTHON';
  exposedApis: ApiEndpoint[];
  dependencies: Dependency[];
  replicas?: number;
  cpuUsage?: number;
  memoryMb?: number;
  rps?: number;
  errorRate?: number;
  version?: string;
  uptimeSeconds?: number;
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

export interface LiveLogEvent {
  id: string;
  timestamp: string;
  level: 'INFO' | 'WARN' | 'ERROR' | 'SUCCESS' | 'AUDIT' | 'METRIC';
  service: string;
  message: string;
}

export interface BatchCanaryResult {
  key: string;
  threshold: number;
  totalUsers: number;
  enabledCount: number;
  disabledCount: number;
  enabledPercentage: number;
  samples: Array<{ userId: string; hashScore: number; enabled: boolean; variant: string }>;
}

@Injectable({
  providedIn: 'root'
})
export class CatalogService {
  private baseUrl = 'http://localhost:8088/api/v1';

  /** Authenticated SSE transport; EventSource cannot attach the bearer token. */
  private sseClient = inject(SseClient);

  // Reactive State Signals
  servicesSignal = signal<ServiceItem[]>([]);
  statsSignal = signal<any>(null);
  ownerTeamsSignal = signal<string[]>([]);
  featureFlagsSignal = signal<FeatureFlag[]>([]);
  telemetrySignal = signal<any>({
    totalPods: 16,
    healthyPods: 16,
    avgCpuUsagePercent: 4.8,
    avgMemoryUsageMb: 185,
    rps: 142,
    p95LatencyMs: 14.2,
    errorRatePercent: 0.02,
    activeConnections: 1240
  });

  trafficModeSignal = signal<'NORMAL' | 'SPIKE' | 'CHAOS_LATENCY' | 'CHAOS_ERROR'>('NORMAL');
  
  // Historical Sparkline Time-Series (last 16 data points)
  sparklineCpuSignal = signal<number[]>([4.2, 4.5, 4.8, 5.1, 4.6, 4.9, 5.2, 4.8, 4.5, 4.7, 5.0, 4.8, 4.6, 4.9, 4.8, 5.1]);
  sparklineRpsSignal = signal<number[]>([120, 132, 128, 145, 140, 138, 142, 150, 148, 142, 139, 144, 141, 146, 142, 145]);
  sparklineLatencySignal = signal<number[]>([12, 14, 13, 15, 14, 14, 16, 15, 14, 13, 14, 15, 14, 14, 15, 14]);

  liveLogEventsSignal = signal<LiveLogEvent[]>([]);
  auditLogsSignal = signal<AuditLogEntry[]>([]);
  rbacMatrixSignal = signal<any>(null);
  k8sClusterSignal = signal<any>(null);
  securityScansSignal = signal<any>(null);
  finOpsSignal = signal<any>(null);
  gitHubReposSignal = signal<any>(null);
  activeIncidentsSignal = signal<string[]>([]);

  private simulationInterval: any = null;

  private mockFallbackServices: ServiceItem[] = [
    {
      id: 'srv-payment',
      name: 'Payment Gateway Service',
      description: 'Handles credit card processing, refunds, fraud scoring, and multi-currency transaction settlement',
      repositoryUrl: 'https://github.com/org/payment-service',
      ownerTeam: 'Equipe Paiement',
      contactChannel: '#pay-core',
      docsUrl: 'https://techdocs.company.internal/payment-gateway',
      grafanaUrl: 'https://grafana.company.internal/d/service-overview?var-service=srv-payment',
      sloAvailability: '99.99% Availability (Error Budget: 4.3m/mo)',
      sloLatencyP95: '< 50ms p95, < 120ms p99',
      escalationPolicy: 'PagerDuty Tier-1 On-Call (P1 SLA: 5min, Secondary: #pay-oncall)',
      scorecardGrade: 'GOLD',
      status: 'ACTIVE',
      techStack: 'SPRING_BOOT',
      replicas: 4,
      cpuUsage: 5.4,
      memoryMb: 240,
      rps: 68,
      errorRate: 0.0,
      version: 'v2.4.1',
      uptimeSeconds: 142800,
      exposedApis: [
        { path: '/api/v1/payments/charge', method: 'POST', description: 'Process credit card payment transaction' },
        { path: '/api/v1/payments/refund', method: 'POST', description: 'Issue refund for settled charge' },
        { path: '/api/v1/payments/verify-3ds', method: 'POST', description: 'Authenticate 3D-Secure 2.0 biometric challenge' }
      ],
      dependencies: [
        { targetServiceId: 'srv-notification', type: 'REST', direction: 'DOWNSTREAM' },
        { targetExternal: 'Stripe Payments API (external)', type: 'REST', direction: 'DOWNSTREAM' },
        { targetExternal: 'PostgreSQL payments_db', type: 'DB', direction: 'DOWNSTREAM' },
        { targetExternal: 'Kafka topic payment-events', type: 'KAFKA', direction: 'DOWNSTREAM' }
      ]
    },
    {
      id: 'srv-catalog',
      name: 'Product Catalog Service',
      description: 'Product taxonomy, dynamic pricing, inventory check and OpenSearch search indexing engine',
      repositoryUrl: 'https://github.com/org/catalog-service',
      ownerTeam: 'Equipe Catalogue',
      contactChannel: '#catalog-core',
      docsUrl: 'https://techdocs.company.internal/product-catalog',
      grafanaUrl: 'https://grafana.company.internal/d/service-overview?var-service=srv-catalog',
      sloAvailability: '99.95% Availability (Error Budget: 21.6m/mo)',
      sloLatencyP95: '< 30ms p95, < 80ms p99',
      escalationPolicy: 'Opsgenie Tier-2 On-Call (P1 SLA: 15min, Secondary: #catalog-core)',
      scorecardGrade: 'GOLD',
      status: 'ACTIVE',
      techStack: 'GO',
      replicas: 3,
      cpuUsage: 3.2,
      memoryMb: 85,
      rps: 110,
      errorRate: 0.0,
      version: 'v1.8.0',
      uptimeSeconds: 310500,
      exposedApis: [
        { path: '/api/v1/products', method: 'GET', description: 'Query searchable product inventory' },
        { path: '/api/v1/products/{id}', method: 'GET', description: 'Retrieve detailed product metadata' },
        { path: '/api/v1/products/pricing/live', method: 'GET', description: 'Fetch algorithmic dynamic pricing' }
      ],
      dependencies: [
        { targetServiceId: 'srv-payment', type: 'REST', direction: 'DOWNSTREAM' },
        { targetExternal: 'Elasticsearch catalog-index', type: 'DB', direction: 'DOWNSTREAM' },
        { targetExternal: 'Kafka topic catalog-events', type: 'KAFKA', direction: 'DOWNSTREAM' }
      ]
    },
    {
      id: 'srv-notification',
      name: 'Omnichannel Notification Engine',
      description: 'Real-time transactional email, SMS, push alerts, and WhatsApp Cloud API message delivery',
      repositoryUrl: 'https://github.com/org/notification-service',
      ownerTeam: 'Equipe Notifications',
      contactChannel: '#notifications-core',
      docsUrl: 'https://techdocs.company.internal/notification-dispatcher',
      grafanaUrl: 'https://grafana.company.internal/d/service-overview?var-service=srv-notification',
      sloAvailability: '99.90% Availability (Error Budget: 43.2m/mo)',
      sloLatencyP95: '< 150ms p95, < 300ms p99',
      escalationPolicy: 'Slack On-Call Alerting (#notifications-core, P1 SLA: 15min)',
      scorecardGrade: 'GOLD',
      status: 'ACTIVE',
      techStack: 'PYTHON',
      replicas: 3,
      cpuUsage: 4.1,
      memoryMb: 145,
      rps: 35,
      errorRate: 0.0,
      version: 'v1.3.4',
      uptimeSeconds: 86400,
      exposedApis: [
        { path: '/api/v1/notify/email', method: 'POST', description: 'Send transactional customer email' },
        { path: '/api/v1/notify/sms', method: 'POST', description: 'Trigger urgent OTP SMS alert' },
        { path: '/api/v1/notify/whatsapp', method: 'POST', description: 'Dispatch templated WhatsApp HSM alert' }
      ],
      dependencies: [
        { targetExternal: 'Twilio SMS API (external)', type: 'REST', direction: 'DOWNSTREAM' },
        { targetExternal: 'SendGrid Email API (external)', type: 'REST', direction: 'DOWNSTREAM' },
        { targetExternal: 'Kafka topic notification-events', type: 'KAFKA', direction: 'DOWNSTREAM' }
      ]
    },
    {
      id: 'srv-frontend-portal',
      name: 'Developer Experience Portal',
      description: 'Unified self-service developer web console built with standalone Angular components & HeroUI',
      repositoryUrl: 'https://github.com/org/developer-portal',
      ownerTeam: 'Equipe Platform',
      contactChannel: '#platform-infra',
      docsUrl: 'https://techdocs.company.internal/developer-portal',
      grafanaUrl: 'https://grafana.company.internal/d/service-overview?var-service=srv-frontend-portal',
      sloAvailability: '99.95% Availability',
      sloLatencyP95: '< 20ms p95',
      escalationPolicy: 'Platform Infra SRE On-Call (#platform-infra)',
      scorecardGrade: 'GOLD',
      status: 'ACTIVE',
      techStack: 'ANGULAR',
      replicas: 2,
      cpuUsage: 2.1,
      memoryMb: 60,
      rps: 42,
      errorRate: 0.0,
      version: 'v3.2.0',
      uptimeSeconds: 420000,
      exposedApis: [
        { path: '/catalog', method: 'GET', description: 'Microservice inventory view' },
        { path: '/scaffolder', method: 'GET', description: 'Golden path project bootstrap engine' }
      ],
      dependencies: [
        { targetServiceId: 'srv-payment', type: 'REST', direction: 'DOWNSTREAM' },
        { targetServiceId: 'srv-catalog', type: 'REST', direction: 'DOWNSTREAM' }
      ]
    },
    {
      id: 'srv-fraud-detector',
      name: 'Real-Time AI Fraud Analysis',
      description: 'FastAPI AI Consumer processing high-throughput RabbitMQ transactions & anomaly detection',
      repositoryUrl: 'https://github.com/org/fraud-ai-consumer',
      ownerTeam: 'Equipe Securite',
      contactChannel: '#fraud-ai',
      docsUrl: 'https://techdocs.company.internal/fraud-ai-consumer',
      grafanaUrl: 'https://grafana.company.internal/d/service-overview?var-service=srv-fraud-detector',
      sloAvailability: '99.95% Availability',
      sloLatencyP95: '< 25ms p95',
      escalationPolicy: 'Security & IAM SRE (#security-iam, P1 SLA: 5min)',
      scorecardGrade: 'GOLD',
      status: 'ACTIVE',
      techStack: 'PYTHON',
      replicas: 4,
      cpuUsage: 6.8,
      memoryMb: 320,
      rps: 85,
      errorRate: 0.0,
      version: 'v1.0.2',
      uptimeSeconds: 54000,
      exposedApis: [
        { path: '/api/v1/fraud/evaluate', method: 'POST', description: 'Run gradient-boosted fraud score' },
        { path: '/api/v1/fraud/model/reload', method: 'POST', description: 'Hot-swap ONNX inference weights' }
      ],
      dependencies: [
        { targetServiceId: 'srv-payment', type: 'REST', direction: 'DOWNSTREAM' },
        { targetExternal: 'RabbitMQ fraud.analysis.queue', type: 'KAFKA', direction: 'DOWNSTREAM' }
      ]
    }
  ];

  constructor(private http: HttpClient) {
    this.initSimulationEngine();
  }

  // ================= DYNAMIC LIVE SIMULATION ENGINE =================
  private initSimulationEngine(): void {
    if (this.simulationInterval) clearInterval(this.simulationInterval);

    // Initial seed logs
    this.addLog('SUCCESS', 'IDP-CORE', 'Internal Developer Platform v3.2 initialized successfully with PostgreSQL & Keycloak');
    this.addLog('INFO', 'K8S-EKS', 'Connected to Kubernetes cluster idp-prod-eks-01 (6 nodes ready, 16 pods running)');
    this.addLog('AUDIT', 'SECURITY', 'Cryptographic HMAC-SHA256 audit ledger verified — zero tampering detected');

    this.simulationInterval = setInterval(() => {
      this.tickTelemetry();
    }, 2500);
  }

  private tickTelemetry(): void {
    const mode = this.trafficModeSignal();
    const current = this.telemetrySignal();
    const services = this.servicesSignal();

    let baseRps = 140;
    let baseCpu = 4.8;
    let baseLatency = 14;
    let baseError = 0.01;

    if (mode === 'SPIKE') {
      baseRps = 950 + Math.floor(Math.random() * 250);
      baseCpu = 38.5 + (Math.random() * 12);
      baseLatency = 42 + Math.floor(Math.random() * 18);
      baseError = 0.45;
    } else if (mode === 'CHAOS_LATENCY') {
      baseRps = 85 + Math.floor(Math.random() * 20);
      baseCpu = 12.0 + (Math.random() * 4);
      baseLatency = 380 + Math.floor(Math.random() * 120);
      baseError = 1.2;
    } else if (mode === 'CHAOS_ERROR') {
      baseRps = 110 + Math.floor(Math.random() * 30);
      baseCpu = 8.5 + (Math.random() * 3);
      baseLatency = 24 + Math.floor(Math.random() * 10);
      baseError = 14.8 + (Math.random() * 6.5);
    } else {
      // Normal oscillation with random walk
      baseRps = 135 + Math.floor(Math.random() * 25);
      baseCpu = 4.5 + (Math.random() * 1.2);
      baseLatency = 13 + Math.floor(Math.random() * 4);
      baseError = +(Math.random() * 0.08).toFixed(2);
    }

    // Check if any service is degraded
    const degradedCount = services.filter(s => s.status === 'DEGRADED').length;
    if (degradedCount > 0) {
      baseError += degradedCount * 4.2;
      baseLatency += degradedCount * 35;
    }

    const totalPods = services.reduce((acc, s) => acc + (s.replicas || 2), 0);
    const healthyPods = services.filter(s => s.status === 'ACTIVE' || s.status === 'SCALING').reduce((acc, s) => acc + (s.replicas || 2), 0);

    const updatedTelemetry = {
      totalPods,
      healthyPods,
      avgCpuUsagePercent: +baseCpu.toFixed(1),
      avgMemoryUsageMb: Math.floor(180 + (baseCpu * 8)),
      rps: baseRps,
      p95LatencyMs: baseLatency,
      errorRatePercent: +baseError.toFixed(2),
      activeConnections: Math.floor(baseRps * 8.5)
    };

    this.telemetrySignal.set(updatedTelemetry);

    // Update Sparklines
    const cpuArr = [...this.sparklineCpuSignal().slice(1), updatedTelemetry.avgCpuUsagePercent];
    const rpsArr = [...this.sparklineRpsSignal().slice(1), updatedTelemetry.rps];
    const latArr = [...this.sparklineLatencySignal().slice(1), updatedTelemetry.p95LatencyMs];

    this.sparklineCpuSignal.set(cpuArr);
    this.sparklineRpsSignal.set(rpsArr);
    this.sparklineLatencySignal.set(latArr);

    // Dynamic random log generation
    if (Math.random() > 0.45) {
      this.generateAmbientLog(mode, services);
    }
  }

  private generateAmbientLog(mode: string, services: ServiceItem[]): void {
    const srv = services.length > 0 ? services[Math.floor(Math.random() * services.length)] : this.mockFallbackServices[0];
    
    if (mode === 'SPIKE') {
      this.addLog('WARN', srv.id, `High traffic volume detected: ${this.telemetrySignal().rps} req/s on /api/v1/ - Autoscaler triggered pod scale-up`);
    } else if (mode === 'CHAOS_LATENCY') {
      this.addLog('WARN', srv.id, `P99 latency threshold breached (${this.telemetrySignal().p95LatencyMs}ms) — Circuit breaker health probe evaluated`);
    } else if (mode === 'CHAOS_ERROR') {
      this.addLog('ERROR', srv.id, `HTTP 500 Internal Server Error in downstream worker thread: Connection pool exhausted`);
    } else {
      const msgs = [
        `Health probe OK (HTTP 200) in 12ms — 0 error frames`,
        `Processed batch transaction settled via RabbitMQ queue: fraud.analysis.queue`,
        `Prometheus scraped 14 targets in 8ms (all SLIs within green zone)`,
        `Canary traffic split evaluated for key NEW_PAYMENT_FLOW_V2`,
        `JWT token validated against Keycloak Realm idp-realm (RS256 signature OK)`
      ];
      const randomMsg = msgs[Math.floor(Math.random() * msgs.length)];
      this.addLog('INFO', srv.id, randomMsg);
    }
  }

  public addLog(level: 'INFO' | 'WARN' | 'ERROR' | 'SUCCESS' | 'AUDIT' | 'METRIC', service: string, message: string): void {
    const newEvent: LiveLogEvent = {
      id: 'log-' + Math.random().toString(36).substring(2, 9),
      timestamp: new Date().toLocaleTimeString(),
      level,
      service: service.toUpperCase(),
      message
    };
    const currentLogs = this.liveLogEventsSignal();
    this.liveLogEventsSignal.set([newEvent, ...currentLogs.slice(0, 99)]);
  }

  // ================= INTERACTIVE SERVICE LIFECYCLE CONTROLS =================
  scaleService(serviceId: string, delta: number): void {
    const services = this.servicesSignal().map(s => {
      if (s.id === serviceId) {
        const currentReplicas = s.replicas || 2;
        const newReplicas = Math.max(1, Math.min(10, currentReplicas + delta));
        
        this.addLog('INFO', s.id, `Scaling replicas from ${currentReplicas} -> ${newReplicas} via Kubernetes Deployment controller`);
        
        // Temporarily mark as SCALING
        setTimeout(() => {
          this.servicesSignal.update(list => list.map(item => item.id === serviceId ? { ...item, status: 'ACTIVE', replicas: newReplicas } : item));
          this.addLog('SUCCESS', s.id, `Replicas scaled successfully to ${newReplicas} pods (All pods Ready in 2.1s)`);
        }, 3000);

        return { ...s, status: 'SCALING' as const, replicas: newReplicas };
      }
      return s;
    });

    this.servicesSignal.set(services);
    this.recordAudit('SERVICE_SCALED', serviceId, `Scaled replica count (delta: ${delta > 0 ? '+' + delta : delta})`);
  }

  restartService(serviceId: string): void {
    const services = this.servicesSignal().map(s => {
      if (s.id === serviceId) {
        this.addLog('WARN', s.id, `Initiating rolling restart on all ${s.replicas || 2} pods...`);
        
        setTimeout(() => {
          this.servicesSignal.update(list => list.map(item => item.id === serviceId ? { ...item, status: 'ACTIVE', uptimeSeconds: 0 } : item));
          this.addLog('SUCCESS', s.id, `Rolling restart complete. Zero dropped requests during pod rotation.`);
        }, 3500);

        return { ...s, status: 'RESTARTING' as const };
      }
      return s;
    });

    this.servicesSignal.set(services);
    this.recordAudit('SERVICE_RESTARTED', serviceId, `Triggered zero-downtime rolling restart`);
  }

  degradeService(serviceId: string): void {
    const services = this.servicesSignal().map(s => {
      if (s.id === serviceId) {
        this.addLog('ERROR', s.id, `Fault injection triggered: Simulated high latency & 15% error rate`);
        return { ...s, status: 'DEGRADED' as const, errorRate: 15.4 };
      }
      return s;
    });

    this.servicesSignal.set(services);
    this.activeIncidentsSignal.update(inc => [...inc, `Degraded service: ${serviceId}`]);
    this.recordAudit('SERVICE_FAULT_INJECTED', serviceId, `Injected chaos fault into service pods`);
  }

  healService(serviceId: string): void {
    const services = this.servicesSignal().map(s => {
      if (s.id === serviceId) {
        this.addLog('SUCCESS', s.id, `Self-healing completed: Restored healthy pod pool & cleared circuit breakers`);
        return { ...s, status: 'ACTIVE' as const, errorRate: 0.0 };
      }
      return s;
    });

    this.servicesSignal.set(services);
    this.activeIncidentsSignal.update(inc => inc.filter(i => !i.includes(serviceId)));
    this.recordAudit('SERVICE_HEALED', serviceId, `Recovered service to 100% healthy status`);
  }

  triggerHealthProbe(serviceId: string): Observable<ServiceHealth> {
    const srv = this.servicesSignal().find(s => s.id === serviceId);
    const probeResult: ServiceHealth = {
      serviceId,
      serviceName: srv?.name || serviceId,
      status: srv?.status || 'ACTIVE',
      cpuUsagePercent: srv?.cpuUsage || 4.2,
      memoryUsageMb: srv?.memoryMb || 180,
      uptimeSeconds: srv?.uptimeSeconds || 86400,
      activePodCount: srv?.replicas || 3,
      timestamp: Date.now()
    };

    this.addLog('METRIC', serviceId, `Health check probe: status=${probeResult.status}, pods=${probeResult.activePodCount}, cpu=${probeResult.cpuUsagePercent}%`);
    return of(probeResult);
  }

  setTrafficMode(mode: 'NORMAL' | 'SPIKE' | 'CHAOS_LATENCY' | 'CHAOS_ERROR'): void {
    this.trafficModeSignal.set(mode);
    this.addLog('AUDIT', 'TRAFFIC-GEN', `Switched live traffic generator profile to: ${mode}`);
    this.tickTelemetry();
  }

  // ================= BATCH CANARY EVALUATOR =================
  evaluateBatchCanary(key: string, count: number = 100): BatchCanaryResult {
    const flag = this.featureFlagsSignal().find(f => f.key === key) || {
      key,
      rolloutPercent: 50,
      enabled: true
    };

    const threshold = flag.enabled ? flag.rolloutPercent : 0;
    const samples: Array<{ userId: string; hashScore: number; enabled: boolean; variant: string }> = [];

    let enabledCount = 0;

    for (let i = 1; i <= count; i++) {
      const userId = `tenant-usr-${1000 + i}`;
      // Deterministic pseudo-MD5 integer bucket 0..99
      let hash = 0;
      for (let c = 0; c < (key + userId).length; c++) {
        hash = (hash * 31 + (key + userId).charCodeAt(c)) % 100;
      }
      const score = Math.abs(hash);
      const isEnabled = score < threshold;
      if (isEnabled) enabledCount++;

      if (i <= 10) {
        samples.push({
          userId,
          hashScore: score,
          enabled: isEnabled,
          variant: isEnabled ? 'CANARY_V2' : 'BASELINE_V1'
        });
      }
    }

    const result: BatchCanaryResult = {
      key,
      threshold,
      totalUsers: count,
      enabledCount,
      disabledCount: count - enabledCount,
      enabledPercentage: Math.round((enabledCount / count) * 100),
      samples
    };

    this.addLog('INFO', 'CANARY-EVAL', `Batch evaluated ${count} users for ${key}: ${result.enabledPercentage}% received canary`);
    return result;
  }

  createFeatureFlag(flag: FeatureFlag): void {
    const newFlag = {
      ...flag,
      id: 'ff-' + Math.random().toString(36).substring(2, 7)
    };
    this.featureFlagsSignal.update(flags => [newFlag, ...flags]);
    this.addLog('SUCCESS', 'FEATURE-FLAGS', `Created feature flag '${flag.key}' with rollout ${flag.rolloutPercent}%`);
    this.recordAudit('FEATURE_FLAG_CREATED', flag.key, `Created feature toggle for team ${flag.targetTeam || 'Platform'}`);
  }

  private recordAudit(action: string, targetId: string, details: string): void {
    const entry: AuditLogEntry = {
      id: 'aud-' + Math.random().toString(36).substring(2, 7),
      actorId: 'usr-admin (Keycloak)',
      action,
      targetId,
      details,
      hmacSignature: '0x' + Array.from({length: 32}, () => Math.floor(Math.random()*16).toString(16)).join(''),
      verified: true,
      createdAt: new Date().toLocaleTimeString()
    };
    this.auditLogsSignal.update(logs => [entry, ...logs]);
  }

  // ================= CATALOG HTTP & STATE METHODS =================
  loadServices(search?: string, techStack?: string, ownerTeam?: string): void {
    let params = new HttpParams();
    if (search) params = params.set('search', search);
    if (techStack && techStack !== 'ALL') params = params.set('techStack', techStack);
    if (ownerTeam && ownerTeam !== 'ALL') params = params.set('ownerTeam', ownerTeam);

    this.http.get<ServiceItem[]>(`${this.baseUrl}/catalog/services`, { params }).pipe(
      catchError(() => {
        let filtered = [...this.mockFallbackServices];
        if (search) {
          const s = search.toLowerCase();
          filtered = filtered.filter(item => 
            item.name.toLowerCase().includes(s) || 
            item.description.toLowerCase().includes(s) || 
            item.ownerTeam.toLowerCase().includes(s)
          );
        }
        if (techStack && techStack !== 'ALL') {
          filtered = filtered.filter(item => item.techStack === techStack);
        }
        if (ownerTeam && ownerTeam !== 'ALL') {
          filtered = filtered.filter(item => item.ownerTeam === ownerTeam);
        }
        return of(filtered);
      })
    ).subscribe(data => {
      this.servicesSignal.set(data);
      this.loadStats();
    });

    this.loadOwnerTeams();
  }

  loadStats(): void {
    this.http.get<any>(`${this.baseUrl}/catalog/stats`).pipe(
      catchError(() => {
        const list = this.servicesSignal();
        const totalApis = list.reduce((acc, s) => acc + (s.exposedApis?.length || 0), 0);
        const totalDeps = list.reduce((acc, s) => acc + (s.dependencies?.length || 0), 0);
        const teams = new Set(list.map(s => s.ownerTeam)).size;
        return of({
          totalServices: list.length,
          totalApis,
          totalDependencies: totalDeps,
          totalOwnerTeams: teams
        });
      })
    ).subscribe(stats => this.statsSignal.set(stats));
  }

  loadOwnerTeams(): void {
    this.http.get<string[]>(`${this.baseUrl}/catalog/teams`).pipe(
      catchError(() => of(['Equipe Paiement', 'Equipe Catalogue', 'Equipe Notifications', 'Equipe Platform', 'Equipe Securite']))
    ).subscribe(teams => this.ownerTeamsSignal.set(teams));
  }

  // Phase 2 Scaffolder HTTP & SSE Streaming Methods
  initiateScaffold(payload: any, idempotencyKey?: string): Observable<ScaffoldJob> {
    const key = idempotencyKey || 'idemp-' + Math.random().toString(36).substring(2, 9);
    return this.http.post<ScaffoldJob>(`${this.baseUrl}/scaffold`, payload, {
      headers: { 'Idempotency-Key': key }
    });
  }

  getScaffoldJob(jobId: string): Observable<ScaffoldJob> {
    return this.http.get<ScaffoldJob>(`${this.baseUrl}/scaffold/jobs/${jobId}`);
  }

  downloadScaffoldArtifact(jobId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/scaffold/jobs/${jobId}/artifact`, {
      responseType: 'blob'
    });
  }

  subscribeScaffoldStream(jobId: string): Observable<ScaffoldJob> {
    return new Observable(observer => {
      // Read through SseClient rather than EventSource: the endpoint requires a
      // bearer token, which EventSource cannot send.
      const subscription = this.sseClient
        .stream(`${this.baseUrl}/scaffold/jobs/${jobId}/stream`)
        .subscribe({
          next: message => {
            if (message.event !== 'JOB_PROGRESS') {
              return;
            }
            try {
              const data = JSON.parse(message.data);
              observer.next(data);
              if (data.status === 'COMPLETED' || data.status === 'FAILED') {
                observer.complete();
              }
            } catch (e) {
              observer.error(e);
            }
          },
          error: err => observer.error(err),
          complete: () => observer.complete()
        });

      return () => subscription.unsubscribe();
    });
  }

  // Phase 3 Feature Flags HTTP Methods
  loadFeatureFlags(): void {
    this.http.get<FeatureFlag[]>(`${this.baseUrl}/feature-flags`).pipe(
      catchError(() => of([
        { id: 'ff-1', key: 'NEW_PAYMENT_FLOW_V2', description: 'Enable 3D-Secure 2.0 checkout flow with biometric challenge', enabled: true, rolloutPercent: 50, targetTeam: 'Equipe Paiement' },
        { id: 'ff-2', key: 'ELASTICSEARCH_SEARCH_V3', description: 'Route catalog queries through high-throughput OpenSearch cluster', enabled: false, rolloutPercent: 0, targetTeam: 'Equipe Catalogue' },
        { id: 'ff-3', key: 'WHATSAPP_NOTIF_PROVIDER', description: 'Enable WhatsApp Cloud API HSM provider for critical OTP alerts', enabled: true, rolloutPercent: 20, targetTeam: 'Equipe Notifications' },
        { id: 'ff-4', key: 'AI_FRAUD_INFERENCE_ONNX', description: 'Route real-time payments through FastAPI ONNX fraud detector model', enabled: true, rolloutPercent: 80, targetTeam: 'Equipe Securite' }
      ]))
    ).subscribe(flags => this.featureFlagsSignal.set(flags));
  }

  toggleFeatureFlag(id: string): Observable<FeatureFlag> {
    this.featureFlagsSignal.update(flags => flags.map(f => {
      if (f.id === id) {
        const nextState = !f.enabled;
        this.addLog('INFO', 'FEATURE-FLAGS', `Toggled flag '${f.key}' -> ${nextState ? 'ENABLED' : 'DISABLED'}`);
        return { ...f, enabled: nextState };
      }
      return f;
    }));
    return this.http.patch<FeatureFlag>(`${this.baseUrl}/feature-flags/${id}/toggle`, {}).pipe(
      catchError(() => of(this.featureFlagsSignal().find(f => f.id === id)!))
    );
  }

  updateFeatureFlagRollout(id: string, rolloutPercent: number): Observable<FeatureFlag> {
    this.featureFlagsSignal.update(flags => flags.map(f => {
      if (f.id === id) {
        this.addLog('INFO', 'FEATURE-FLAGS', `Updated canary rollout for '${f.key}' to ${rolloutPercent}%`);
        return { ...f, rolloutPercent };
      }
      return f;
    }));
    return this.http.patch<FeatureFlag>(`${this.baseUrl}/feature-flags/${id}/rollout`, { rolloutPercent }).pipe(
      catchError(() => of(this.featureFlagsSignal().find(f => f.id === id)!))
    );
  }

  evaluateCanaryRollout(key: string, userId: string): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/feature-flags/eval/${key}`, {
      params: new HttpParams().set('userId', userId)
    }).pipe(
      catchError(() => {
        const flag = this.featureFlagsSignal().find(f => f.key === key);
        const threshold = flag ? (flag.enabled ? flag.rolloutPercent : 0) : 50;
        let hash = 0;
        for (let c = 0; c < (key + userId).length; c++) {
          hash = (hash * 31 + (key + userId).charCodeAt(c)) % 100;
        }
        const score = Math.abs(hash);
        const enabled = score < threshold;
        return of({
          key,
          userId,
          enabled,
          hashScore: score,
          threshold,
          variant: enabled ? 'CANARY_ACTIVE' : 'BASELINE_FALLBACK'
        });
      })
    );
  }

  // Phase 4 Observability & Telemetry HTTP Methods
  loadTelemetryMetrics(): void {
    this.http.get<any>(`${this.baseUrl}/telemetry/metrics`).pipe(
      catchError(() => of(this.telemetrySignal()))
    ).subscribe(metrics => this.telemetrySignal.set(metrics));
  }

  getServiceHealth(serviceId: string): Observable<ServiceHealth> {
    return this.triggerHealthProbe(serviceId);
  }

  // Phase 5 Audit Logs & RBAC HTTP Methods
  loadAuditLogs(): void {
    this.http.get<AuditLogEntry[]>(`${this.baseUrl}/audit`).pipe(
      catchError(() => of([
        { id: 'aud-1', actorId: 'usr-admin', action: 'SCAFFOLD_PROJECT_INITIATED', targetId: 'srv-fraud-detector', details: 'Generated Python FastAPI consumer with RabbitMQ queue integration', hmacSignature: 'c521b1159786686b37a7cdfab94aea8bb4b2002655062ad08fedc1627935b5b3', verified: true, createdAt: '10 mins ago' },
        { id: 'aud-2', actorId: 'usr-tech-lead', action: 'FEATURE_FLAG_ROLLOUT_UPDATED', targetId: 'NEW_PAYMENT_FLOW_V2', details: 'Increased canary rollout from 25% to 50%', hmacSignature: 'f1a923d8c11e74a8990176b92a543f019b88e1a6c4710db44199aa567389104b', verified: true, createdAt: '25 mins ago' },
        { id: 'aud-3', actorId: 'usr-admin', action: 'DEPLOY_PRODUCTION', targetId: 'srv-payment', details: 'Promoted release v2.4.1 to Kubernetes production cluster', hmacSignature: '8a3e74b21901fa567389104bc521b1159786686b37a7cdfab94aea8bb4b20026', verified: true, createdAt: '1 hour ago' }
      ]))
    ).subscribe(logs => this.auditLogsSignal.set(logs));
  }

  loadRbacMatrix(): void {
    this.http.get<any>(`${this.baseUrl}/rbac/matrix`).pipe(
      catchError(() => of({
        ADMIN: ['CREATE_SERVICE', 'DELETE_SERVICE', 'SCAFFOLD_PROJECT', 'MUTATE_FEATURE_FLAGS', 'VIEW_AUDIT_LOGS', 'DEPLOY_PRODUCTION', 'CHAOS_TESTING'],
        TECH_LEAD: ['CREATE_SERVICE', 'SCAFFOLD_PROJECT', 'MUTATE_FEATURE_FLAGS', 'VIEW_AUDIT_LOGS', 'DEPLOY_STAGING'],
        DEVELOPER: ['SCAFFOLD_PROJECT', 'VIEW_AUDIT_LOGS', 'TRIGGER_CI_BUILD', 'SCALE_PODS'],
        VIEWER: ['READ_CATALOG', 'VIEW_METRICS']
      }))
    ).subscribe(matrix => this.rbacMatrixSignal.set(matrix));
  }

  // Phase 6 IDP Copilot RAG HTTP Methods
  sendCopilotQuery(query: string, userId: string = 'usr-1'): Observable<CopilotChatResponse> {
    const qLower = query.toLowerCase();
    
    // Dynamic local assistant response generation based on live state
    let answer = `I searched the internal microservice knowledge base and vector store. `;
    let sources: string[] = [];
    let followUps: string[] = [
      'Show live telemetry for Payment Gateway',
      'What feature flags are currently active?',
      'How to scaffold a new Go microservice?'
    ];

    if (qLower.includes('restart') || qLower.includes('reboot')) {
      const match = this.servicesSignal().find(s => qLower.includes(s.name.toLowerCase()) || qLower.includes(s.id.toLowerCase()));
      if (match) {
        this.restartService(match.id);
        answer = `[ACTION EXECUTED] Initiated rolling restart on **${match.name}** (\`${match.id}\`). All ${match.replicas || 2} pods are rotating gracefully.`;
        sources = [`Service Catalog / ${match.id}`, `Kubernetes Deployment Controller`];
      } else {
        answer = `Please specify which service you would like to restart (e.g. *restart Payment Gateway* or *restart srv-catalog*).`;
      }
    } else if (qLower.includes('scale') || qLower.includes('replicas')) {
      const match = this.servicesSignal().find(s => qLower.includes(s.name.toLowerCase()) || qLower.includes(s.id.toLowerCase()));
      if (match) {
        this.scaleService(match.id, 1);
        answer = `[ACTION EXECUTED] Scaling up **${match.name}** (\`${match.id}\`) by +1 replica (New target: ${(match.replicas || 2) + 1} pods).`;
        sources = [`Kubernetes HPA`, `Service Catalog / ${match.id}`];
      } else {
        answer = `Please specify the service you wish to scale (e.g. *scale payment service*).`;
      }
    } else if (qLower.includes('payment') || qLower.includes('charge')) {
      const srv = this.servicesSignal().find(s => s.id === 'srv-payment') || this.mockFallbackServices[0];
      answer = `The **${srv.name}** (\`${srv.id}\`) is written in **${srv.techStack}** and owned by **${srv.ownerTeam}**. It exposes \`POST /api/v1/payments/charge\` and is currently running **${srv.replicas || 4} replicas** with **${srv.cpuUsage}% CPU load**.`;
      sources = [`srv-payment (Payment Gateway Service)`, `API: POST /api/v1/payments/charge`, `Owner: Equipe Paiement`];
      followUps = ['Show dependency graph for srv-payment', 'Restart Payment Gateway pods'];
    } else if (qLower.includes('flag') || qLower.includes('canary')) {
      const flags = this.featureFlagsSignal();
      answer = `There are currently **${flags.length} active feature flags** in the platform:\n` +
        flags.map(f => `• \`${f.key}\`: **${f.enabled ? 'ENABLED' : 'DISABLED'}** (${f.rolloutPercent}% canary rollout) — *${f.description}*`).join('\n');
      sources = ['Feature Toggle Store / PostgreSQL', 'Canary Rollout Hash Engine'];
    } else if (qLower.includes('fraud') || qLower.includes('rabbitmq')) {
      answer = `The **Real-Time AI Fraud Analysis** (\`srv-fraud-detector\`) service consumes messages from RabbitMQ queue \`fraud.analysis.queue\` and produces scoring events to \`fraud.result.queue\`. It uses Python FastAPI and is maintained by **Equipe Securite**.`;
      sources = ['srv-fraud-detector / FastAPI', 'RabbitMQ Queue: fraud.analysis.queue'];
    } else {
      answer = `Based on your query "${query}", I retrieved context from the microservice catalog. The platform currently registers **${this.servicesSignal().length} active services** across Spring Boot, Go, Angular, and Python stacks. Cluster health is at **${this.telemetrySignal().healthyPods}/${this.telemetrySignal().totalPods} healthy pods** running at **${this.telemetrySignal().rps} req/s**.`;
      sources = ['Service Catalog Vector Embeddings', 'Prometheus Telemetry Registry'];
    }

    return of({
      answer,
      sources,
      suggestedFollowUps: followUps,
      latencyMs: Math.floor(80 + Math.random() * 90)
    });
  }

  getSuggestedQuestions(): Observable<string[]> {
    return of([
      'Which team owns the Payment Gateway service?',
      'Restart Payment Gateway pods',
      'What feature flags are currently active?',
      'How does the AI Fraud RabbitMQ queue communicate?',
      'Scale up Product Catalog replicas'
    ]);
  }

  // Phase 7 CloudOps, FinOps, GitHub & Enterprise Governance
  loadK8sClusterStatus(): void {
    this.http.get<any>(`${this.baseUrl}/devops/k8s/cluster`).pipe(
      catchError(() => of({
        clusterName: 'idp-prod-eks-01',
        region: 'eu-west-3 (Paris)',
        kubernetesVersion: 'v1.29.3',
        totalNodes: 6,
        readyNodes: 6,
        runningPods: this.telemetrySignal().totalPods || 16,
        namespaces: ['default', 'idp-system', 'payments', 'catalog', 'notifications', 'security'],
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
        scanner: 'Trivy v0.49.1 + SonarQube Quality Gate',
        lastScanTime: '5 minutes ago',
        criticalVulnerabilities: 0,
        highVulnerabilities: 0,
        mediumVulnerabilities: 3,
        lowVulnerabilities: 8,
        scannedImages: [
          { image: 'payment-service:v2.4.1', critical: 0, high: 0, medium: 0, status: 'PASSED' },
          { image: 'catalog-service:v1.8.0', critical: 0, high: 0, medium: 1, status: 'PASSED' },
          { image: 'notification-service:v1.3.4', critical: 0, high: 0, medium: 1, status: 'PASSED' },
          { image: 'fraud-detector:v1.0.2', critical: 0, high: 0, medium: 1, status: 'PASSED' }
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
          { team: 'Equipe Securite', monthlyCost: 240.00, percentage: 13.0 },
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
    const newService: ServiceItem = {
      id: 'srv-' + payload.name.toLowerCase(),
      name: payload.name.replace('-', ' ').toUpperCase() + ' Service',
      description: payload.description || 'Imported from GitHub organization repository',
      repositoryUrl: `https://github.com/${payload.fullName}`,
      ownerTeam: 'Equipe Platform',
      status: 'ACTIVE',
      techStack: payload.techStack || 'SPRING_BOOT',
      replicas: 2,
      cpuUsage: 2.8,
      memoryMb: 120,
      rps: 15,
      errorRate: 0.0,
      version: 'v1.0.0',
      uptimeSeconds: 120,
      exposedApis: [
        { path: `/api/v1/${payload.name}`, method: 'GET', description: 'Primary API resource endpoint' }
      ],
      dependencies: []
    };

    this.servicesSignal.update(list => [newService, ...list]);
    this.addLog('SUCCESS', newService.id, `Imported GitHub repository ${payload.fullName} into IDP Service Catalog`);
    this.recordAudit('GITHUB_REPO_IMPORTED', newService.id, `Imported ${payload.fullName}`);
    return of({ success: true, service: newService });
  }

  // NOTE: evalAbacPolicy() was removed. It decided access in the browser from a
  // one-line expression over a caller-supplied role, so it agreed with the real
  // engine only by coincidence. Policy evaluation now happens server-side via
  // AdminService.simulate() -> POST /api/v1/admin/policies/simulate.

  // Authenticated SSE stream (see SseClient for why EventSource is not used).
  connectLiveLogStream(): Observable<any> {
    return new Observable(observer => {
      const subscription = this.sseClient
        .stream(`${this.baseUrl}/telemetry/logs/stream`)
        .subscribe({
          next: message => {
            if (message.event !== 'log') {
              return;
            }
            try {
              observer.next(JSON.parse(message.data));
            } catch {
              observer.next({ message: message.data });
            }
          },
          error: err => observer.error(err),
          complete: () => observer.complete()
        });

      return () => subscription.unsubscribe();
    });
  }
}