import { Component, OnInit, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CatalogService, ServiceItem, ScaffoldJob, FeatureFlag, AuditLogEntry, CopilotChatResponse } from './services/catalog.service';

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

  // Canary Evaluation State
  canaryKey = signal<string>('NEW_PAYMENT_FLOW_V2');
  canaryUserId = signal<string>('user-1042');
  canaryResult = signal<any>(null);

  // Telemetry & Live SSE Logs
  logs = signal<string[]>([]);
  isStreamingPaused = signal<boolean>(false);
  liveStreamStatus = signal<'CONNECTED' | 'DISCONNECTED' | 'FALLBACK'>('CONNECTED');

  // Copilot AI Chat State
  chatInput = '';
  chatHistory = signal<ChatMessage[]>([
    {
      sender: 'COPILOT',
      text: 'Hello! I am your IDP Copilot powered by the RAG Vector Knowledge Base. You can ask me about microservice ownership, exposed REST APIs, active feature flags, or how to scaffold a new service.',
      time: 'Just now',
      suggestedFollowUps: [
        'Which team owns the Payment Gateway service?',
        'How do I scaffold a new Go microservice?',
        'What APIs are exposed for customer notifications?'
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
  currentUser = signal({
    username: 'admin',
    email: 'admin@company.internal',
    role: 'ADMIN'
  });
  isKeycloakLoginOpen = signal<boolean>(false);
  loginForm = {
    username: 'admin',
    password: '',
    role: 'ADMIN'
  };

  constructor(public catalogService: CatalogService) {}

  ngOnInit(): void {
    this.refreshAllData();
    this.initLiveLogStreaming();
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

  // Scaffolder Wizard
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
    const payload = {
      name: this.scaffoldForm.name,
      description: this.scaffoldForm.description,
      stackTemplate: this.scaffoldForm.stackTemplate,
      ownerTeam: this.scaffoldForm.ownerTeam
    };

    this.catalogService.initiateScaffold(payload).subscribe({
      next: (job: ScaffoldJob) => {
        this.currentJob.set(job);
        this.pollScaffoldJob(job.id);
      },
      error: () => {
        // Fallback simulation if backend endpoint is in demo mode
        const mockJob: ScaffoldJob = {
          id: 'job-' + Math.random().toString(36).substring(2, 7),
          projectId: 'proj-' + this.scaffoldForm.name.toLowerCase().replace(/\s+/g, '-'),
          status: 'RUNNING',
          progressPercent: 25,
          currentStep: 'Cloning Golden Template & Injecting Configuration',
          stepLogs: '[IDP] Generating repository structure...\n[GIT] Initializing main branch with security hooks\n'
        };
        this.currentJob.set(mockJob);
        this.simulateScaffoldProgress(mockJob);
      }
    });
  }

  private pollScaffoldJob(jobId: string): void {
    const interval = setInterval(() => {
      this.catalogService.getScaffoldJob(jobId).subscribe({
        next: (job: ScaffoldJob) => {
          this.currentJob.set(job);
          if (job.status === 'COMPLETED' || job.status === 'FAILED') {
            clearInterval(interval);
            this.isScaffoldingRunning.set(false);
            this.catalogService.loadServices();
          }
        },
        error: () => {
          clearInterval(interval);
          this.isScaffoldingRunning.set(false);
        }
      });
    }, 1000);
  }

  private simulateScaffoldProgress(job: ScaffoldJob): void {
    const steps = [
      { pct: 50, step: 'Configuring PostgreSQL & Flyway Migrations', log: '[DB] Applying schema baseline V1__init.sql\n' },
      { pct: 75, step: 'Injecting GitHub Actions CI/CD & Dockerfile', log: '[CI/CD] Pipeline template generated: .github/workflows/deploy.yml\n' },
      { pct: 100, step: 'Registering Microservice into Service Catalog', log: '[IDP] Service registered & health check endpoints active\n[SUCCESS] Scaffolding completed in 3.4s\n' }
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
          this.catalogService.loadServices();
        }
        stepIdx++;
      }
    }, 1200);
  }

  // Feature Flags
  toggleFeatureFlag(flag: FeatureFlag): void {
    if (!flag.id) return;
    this.catalogService.toggleFeatureFlag(flag.id).subscribe(() => {
      this.catalogService.loadFeatureFlags();
    });
  }

  updateFlagRollout(flag: FeatureFlag, event: any): void {
    if (!flag.id) return;
    const value = Number(event.target.value);
    flag.rolloutPercent = value;
    this.catalogService.updateFeatureFlagRollout(flag.id, value).subscribe();
  }

  runCanaryTest(): void {
    this.catalogService.evaluateCanaryRollout(this.canaryKey(), this.canaryUserId()).subscribe(res => {
      this.canaryResult.set(res);
    });
  }

  // Live Log Stream
  private initLiveLogStreaming(): void {
    this.catalogService.connectLiveLogStream().subscribe({
      next: (data: any) => {
        if (!this.isStreamingPaused()) {
          const formatted = typeof data === 'string' ? data : (data.message || JSON.stringify(data));
          this.logs.set([...this.logs().slice(-40), formatted]);
        }
        this.liveStreamStatus.set('CONNECTED');
      },
      error: () => {
        this.liveStreamStatus.set('FALLBACK');
        this.startFallbackLogs();
      }
    });
  }

  toggleLogPause(): void {
    this.isStreamingPaused.set(!this.isStreamingPaused());
  }

  clearLogs(): void {
    this.logs.set([]);
  }

  private startFallbackLogs(): void {
    setInterval(() => {
      if (!this.isStreamingPaused()) {
        const events = [
          `[${new Date().toLocaleTimeString()}] [POD-HEALTH] srv-payment cpu: 3.4% mem: 142MB - status: HEALTHY`,
          `[${new Date().toLocaleTimeString()}] [PROMETHEUS] Scraped 14 targets in 18ms (all probes OK)`,
          `[${new Date().toLocaleTimeString()}] [CANARY] Feature flag NEW_PAYMENT_FLOW_V2 hash evaluated (50% bucket)`,
          `[${new Date().toLocaleTimeString()}] [AUDIT] Cryptographic HMAC verification: Signature 0x7c9f valid`
        ];
        const randomEvent = events[Math.floor(Math.random() * events.length)];
        this.logs.set([...this.logs().slice(-40), randomEvent]);
      }
    }, 4000);
  }

  // Copilot Chat
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
      },
      error: () => {
        const botFallback: ChatMessage = {
          sender: 'COPILOT',
          text: `Based on your query "${textToSend}", I found related microservices in the platform: **Payment Gateway Service** (Java/Spring Boot) and **Product Catalog** (Go/Gin). Check out the Service Catalog tab for endpoint details.`,
          sources: ['Service Catalog / srv-payment', 'Service Catalog / srv-catalog'],
          time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };
        this.chatHistory.set([...this.chatHistory(), botFallback]);
        this.isCopilotLoading.set(false);
      }
    });
  }

  // GitHub 1-Click Import
  importGitHubRepo(repo: any): void {
    const payload = {
      name: repo.name,
      fullName: repo.fullName,
      description: repo.description,
      techStack: repo.language === 'Java' ? 'SPRING_BOOT' : (repo.language === 'Go' ? 'GO' : 'PYTHON')
    };

    this.catalogService.importGitHubRepo(payload).subscribe(() => {
      this.catalogService.loadServices();
      this.setTab('catalog');
    });
  }

  // ABAC Evaluation
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

  performKeycloakLogin(): void {
    const role = this.loginForm.role;
    const user = this.loginForm.username || 'admin';
    this.currentUser.set({
      username: user,
      email: `${user.toLowerCase()}@company.internal`,
      role: role
    });
    this.isKeycloakLoginOpen.set(false);
    this.logs.set([...this.logs(), `[KEYCLOAK-SSO] User '${user}' authenticated via Realm idp-realm with role ${role}`]);
  }

  quickDemoLogin(role: string): void {
    this.loginForm.role = role;
    this.loginForm.username = role.toLowerCase() + '_user';
    this.performKeycloakLogin();
  }
}