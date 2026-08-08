// IDP Platform Main Frontend Logic - HeroUI & Enterprise REST Integration

const API_BASE_URL = '/api/v1';

// Initial Mock Seed fallback if backend is offline or connecting
const initialServices = [
  {
    id: 'srv-payment',
    name: 'Payment Gateway Service',
    description: 'Handles card processing, refunds and transaction settlement with stripe integration.',
    repositoryUrl: 'https://github.com/org/payment-service',
    ownerTeam: 'Equipe Paiement',
    status: 'ACTIVE',
    techStack: 'SPRING_BOOT',
    exposedApis: [
      { path: '/api/v1/payments/charge', method: 'POST', description: 'Process credit card' },
      { path: '/api/v1/payments/{id}/refund', method: 'POST', description: 'Initiate refund' }
    ]
  },
  {
    id: 'srv-catalog',
    name: 'Product Catalog API',
    description: 'Manages inventory, categories, pricing search indexes and Elasticsearch sync.',
    repositoryUrl: 'https://github.com/org/catalog-service',
    ownerTeam: 'Equipe Catalogue',
    status: 'ACTIVE',
    techStack: 'ANGULAR',
    exposedApis: [
      { path: '/api/v1/products', method: 'GET', description: 'Retrieve paginated list of catalog products' }
    ]
  },
  {
    id: 'srv-notification',
    name: 'Notification Dispatcher',
    description: 'Dispatches SMS, Email, and Push Notifications via Twilio and SendGrid.',
    repositoryUrl: 'https://github.com/org/notification-service',
    ownerTeam: 'Equipe Notifications',
    status: 'ACTIVE',
    techStack: 'GO',
    exposedApis: [
      { path: '/api/v1/notifications/send', method: 'POST', description: 'Send transactional email or push alert' }
    ]
  }
];

let featureFlags = [
  { id: 1, flagKey: 'NEW_PAYMENT_FLOW_V2', serviceName: 'Payment Gateway', enabled: true, rolloutPercent: 50, targetTeam: 'Equipe Paiement' },
  { id: 2, flagKey: 'ELASTICSEARCH_SEARCH_V3', serviceName: 'Product Catalog API', enabled: false, rolloutPercent: 0, targetTeam: 'Equipe Catalogue' },
  { id: 3, flagKey: 'WHATSAPP_NOTIF_PROVIDER', serviceName: 'Notification Dispatcher', enabled: true, rolloutPercent: 10, targetTeam: 'Equipe Notifications' }
];

function initApp() {
  initThemeToggle();
  initGitHubIntegration();
  initTabs();
  fetchServices();
  fetchFeatureFlags();
  initDependencyGraph();
  initLiveLogs();
  initCopilot();
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initApp);
} else {
  initApp();
}

// GitHub OAuth & Repository Picker Integration
function initGitHubIntegration() {
  const connectBtn = document.getElementById('btn-github-connect');
  const modal = document.getElementById('github-modal');
  const closeBtn = document.getElementById('close-github-modal');
  const container = document.getElementById('github-repos-container');

  if (!connectBtn || !modal) return;

  connectBtn.addEventListener('click', async () => {
    modal.classList.remove('hidden');
    container.innerHTML = '<div class="text-xs text-slate-400 p-4 text-center">Connecting to GitHub OAuth API...</div>';

    try {
      const res = await fetch(`${API_BASE_URL}/github/repositories?username=octocat`);
      if (res.ok) {
        const data = await res.json();
        renderGitHubRepos(data.repositories || []);
      }
    } catch (err) {
      console.warn('GitHub API fetch failed:', err);
      container.innerHTML = '<div class="text-xs text-red-400 p-4 text-center">Failed to load GitHub repositories.</div>';
    }
  });

  if (closeBtn) {
    closeBtn.addEventListener('click', () => modal.classList.add('hidden'));
  }
}

function renderGitHubRepos(repos) {
  const container = document.getElementById('github-repos-container');
  if (!container) return;
  container.innerHTML = '';

  repos.forEach(repo => {
    const item = document.createElement('div');
    item.className = 'p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-800/50 flex items-center justify-between gap-4 hover:border-indigo-500 transition-all';

    item.innerHTML = `
      <div class="space-y-1">
        <div class="flex items-center gap-2">
          <span class="font-bold text-xs text-slate-900 dark:text-white font-heading">${repo.name}</span>
          <span class="px-2 py-0.2 rounded text-[9px] font-semibold ${repo.isPrivate ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-400' : 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-400'}">${repo.isPrivate ? 'Private' : 'Public'}</span>
        </div>
        <p class="text-[11px] text-slate-500 dark:text-slate-400 line-clamp-1">${repo.description || 'No description'}</p>
        <div class="flex items-center gap-3 text-[10px] text-slate-400 font-mono">
          <span>⭐ ${repo.stars} stars</span>
          <span>${repo.language}</span>
        </div>
      </div>
      <button class="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-semibold shrink-0 cursor-pointer shadow-sm import-repo-btn">
        Import
      </button>
    `;

    item.querySelector('.import-repo-btn').addEventListener('click', async () => {
      item.querySelector('.import-repo-btn').textContent = 'Importing...';
      try {
        const res = await fetch(`${API_BASE_URL}/github/import`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(repo)
        });

        if (res.ok) {
          document.getElementById('github-modal').classList.add('hidden');
          fetchServices();
        }
      } catch (err) {
        console.error('Import failed:', err);
      }
    });

    container.appendChild(item);
  });
}

// Theme Switcher for TailwindCSS (toggle 'dark' class on html element)
function initThemeToggle() {
  const toggleBtn = document.getElementById('theme-toggle');
  const iconSpan = document.getElementById('theme-icon');
  const textSpan = document.getElementById('theme-text');

  if (!toggleBtn) return;

  toggleBtn.addEventListener('click', () => {
    document.documentElement.classList.toggle('dark');
    const isDark = document.documentElement.classList.contains('dark');

    if (iconSpan) iconSpan.textContent = isDark ? '☀️' : '🌙';
    if (textSpan) textSpan.textContent = isDark ? 'Mode Clair' : 'Mode Sombre';
  });
}

// Tab Navigation for TailwindCSS
function initTabs() {
  const menuItems = document.querySelectorAll('.menu-item');
  const tabPanes = document.querySelectorAll('.tab-pane');

  menuItems.forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      const targetTab = item.getAttribute('data-tab');

      menuItems.forEach(m => m.classList.remove('active', 'bg-indigo-50', 'dark:bg-slate-800/80', 'text-indigo-600', 'dark:text-indigo-400'));
      tabPanes.forEach(p => p.classList.add('hidden'));

      item.classList.add('active', 'bg-indigo-50', 'dark:bg-slate-800/80', 'text-indigo-600', 'dark:text-indigo-400');
      const pane = document.getElementById(`tab-${targetTab}`);
      if (pane) {
        pane.classList.remove('hidden');
      }
    });
  });
}

// Fetch Services from Spring Boot REST API
async function fetchServices() {
  const container = document.getElementById('services-container');
  if (!container) return;

  try {
    const res = await fetch(`${API_BASE_URL}/catalog/services`);
    if (res.ok) {
      const services = await res.json();
      renderCatalogCards(services.length > 0 ? services : initialServices);
    } else {
      renderCatalogCards(initialServices);
    }
  } catch (err) {
    console.warn('Backend API connection fallback to local cache:', err);
    renderCatalogCards(initialServices);
  }
}

// Fetch Feature Flags from Spring Boot REST API
async function fetchFeatureFlags() {
  try {
    const res = await fetch(`${API_BASE_URL}/feature-flags`);
    if (res.ok) {
      const flags = await res.json();
      if (flags && flags.length > 0) {
        featureFlags = flags;
      }
    }
  } catch (err) {
    console.warn('Feature flags API fallback to local cache:', err);
  }
  renderFeatureFlags();
}

function renderCatalogCards(services) {
  const container = document.getElementById('services-container');
  if (!container) return;
  container.innerHTML = '';

  services.forEach(srv => {
    const card = document.createElement('div');
    card.className = 'bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800/80 rounded-2xl p-6 shadow-sm hover:shadow-xl hover:border-indigo-500/50 transition-all duration-300 flex flex-col justify-between group';
    
    const stackColor = srv.techStack === 'SPRING_BOOT' ? 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800' :
                       srv.techStack === 'ANGULAR' ? 'bg-red-50 dark:bg-red-950/60 text-red-600 dark:text-red-400 border-red-200 dark:border-red-800' :
                       'bg-sky-50 dark:bg-sky-950/60 text-sky-600 dark:text-sky-400 border-sky-200 dark:border-sky-800';

    let apiListHtml = '';
    if (srv.exposedApis && srv.exposedApis.length > 0) {
      apiListHtml = `
        <div class="mt-3 pt-2 border-t border-slate-100 dark:border-slate-800/60 space-y-1">
          <span class="text-[10px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider">OpenAPI Endpoints:</span>
          ${srv.exposedApis.map(ep => `
            <div class="flex items-center justify-between text-[10px] font-mono">
              <span class="px-1.5 py-0.2 rounded font-bold ${ep.method === 'GET' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-400' : 'bg-indigo-100 text-indigo-700 dark:bg-indigo-950 dark:text-indigo-400'}">${ep.method}</span>
              <span class="text-slate-700 dark:text-slate-300 truncate max-w-[180px]">${ep.path}</span>
            </div>
          `).join('')}
        </div>
      `;
    }

    card.innerHTML = `
      <div class="space-y-3">
        <div class="flex items-start justify-between gap-2">
          <div class="flex flex-col">
            <h3 class="font-heading font-bold text-base text-slate-900 dark:text-white group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">${srv.name}</h3>
            <span class="text-[10px] text-slate-400 font-mono">ID: ${srv.id}</span>
          </div>
          <div class="flex items-center gap-1.5">
            <span class="px-2 py-0.5 rounded text-[9px] font-extrabold bg-indigo-50 dark:bg-indigo-950 text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-800">${srv.environment || 'PROD'}</span>
            <span class="px-2.5 py-0.5 rounded-full text-[10px] font-bold border ${stackColor}">${srv.techStack}</span>
          </div>
        </div>
        <p class="text-xs text-slate-500 dark:text-slate-400 line-clamp-2 leading-relaxed">${srv.description || 'No description provided.'}</p>

        ${apiListHtml}
      </div>

      <div class="mt-6 pt-4 border-t border-slate-100 dark:border-slate-800/80 flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400">
        <div class="flex items-center gap-2">
          <span>Team: <strong class="text-slate-800 dark:text-slate-200">${srv.ownerTeam}</strong></span>
          <a href="${srv.repositoryUrl || '#'}" target="_blank" class="text-indigo-600 dark:text-indigo-400 hover:underline font-semibold flex items-center gap-1">
            GitHub
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
          </a>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-slate-400 text-[10px]">${srv.latencyMs || 12}ms</span>
          <span class="text-emerald-600 dark:text-emerald-400 font-bold">${srv.healthPercent || 99.9}% UP</span>
        </div>
      </div>
    `;
    container.appendChild(card);
  });
}

// Feature Flags Table Renderer with TailwindCSS
function renderFeatureFlags() {
  const tbody = document.getElementById('flags-table-body');
  if (!tbody) return;
  tbody.innerHTML = '';

  featureFlags.forEach((flag, idx) => {
    const tr = document.createElement('tr');
    tr.className = 'hover:bg-slate-50 dark:hover:bg-slate-800/40 transition-colors';
    
    tr.innerHTML = `
      <td class="px-6 py-4 font-mono text-xs font-semibold text-indigo-600 dark:text-indigo-400">${flag.flagKey || flag.key}</td>
      <td class="px-6 py-4 text-xs font-medium text-slate-800 dark:text-slate-200">${flag.serviceName || flag.service}</td>
      <td class="px-6 py-4">
        <span class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-bold ${flag.enabled || flag.status ? 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-800' : 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400'}">
          <span class="w-1.5 h-1.5 rounded-full ${flag.enabled || flag.status ? 'bg-emerald-500 animate-pulse' : 'bg-slate-400'}"></span>
          ${flag.enabled || flag.status ? 'ACTIVE' : 'INACTIVE'}
        </span>
      </td>
      <td class="px-6 py-4">
        <div class="flex items-center gap-3">
          <input type="range" min="0" max="100" value="${flag.rolloutPercent !== undefined ? flag.rolloutPercent : flag.rollout}" class="w-24 accent-indigo-600 rollout-slider" data-idx="${idx}" />
          <span class="font-mono text-xs text-slate-700 dark:text-slate-300 font-semibold">${flag.rolloutPercent !== undefined ? flag.rolloutPercent : flag.rollout}%</span>
        </div>
      </td>
      <td class="px-6 py-4 text-xs text-slate-600 dark:text-slate-400">${flag.targetTeam || flag.team}</td>
      <td class="px-6 py-4">
        <button class="px-3 py-1 text-[11px] font-semibold rounded-lg border border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-800 transition-all toggle-btn cursor-pointer" data-idx="${idx}">
          ${flag.enabled || flag.status ? 'Disable' : 'Enable'}
        </button>
      </td>
    `;
    tbody.appendChild(tr);
  });

  document.querySelectorAll('.toggle-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const idx = e.target.getAttribute('data-idx');
      featureFlags[idx].status = !featureFlags[idx].status;
      renderFeatureFlags();
    });
  });

  document.querySelectorAll('.rollout-slider').forEach(slider => {
    slider.addEventListener('change', (e) => {
      const idx = e.target.getAttribute('data-idx');
      featureFlags[idx].rollout = e.target.value;
      renderFeatureFlags();
    });
  });
}

// Interactive Dependency Graph Rendering via SVG
function initDependencyGraph() {
  const viewport = document.getElementById('graph-view');
  viewport.innerHTML = `
    <svg width="100%" height="100%" viewBox="0 0 800 450" xmlns="http://www.w3.org/2000/svg">
      <!-- Glow Filters -->
      <defs>
        <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="4" result="blur" />
          <feComposite in="SourceGraphic" in2="blur" operator="over" />
        </filter>
      </defs>

      <!-- Connections -->
      <line x1="200" y1="200" x2="400" y2="120" stroke="#6366f1" stroke-width="2" stroke-dasharray="6,6" />
      <line x1="400" y1="120" x2="600" y2="200" stroke="#ec4899" stroke-width="2" />
      <line x1="200" y1="200" x2="200" y2="350" stroke="#10b981" stroke-width="2" />
      <line x1="400" y1="120" x2="400" y2="350" stroke="#f59e0b" stroke-width="2" />

      <!-- Nodes -->
      <!-- Service A: Payment -->
      <g transform="translate(200, 200)">
        <rect x="-80" y="-30" width="160" height="60" rx="10" fill="#1e1b4b" stroke="#6366f1" stroke-width="2" filter="url(#glow)"/>
        <text x="0" y="-5" text-anchor="middle" fill="#ffffff" font-family="Outfit" font-weight="600" font-size="14">Payment Gateway</text>
        <text x="0" y="15" text-anchor="middle" fill="#9ca3af" font-family="Inter" font-size="11">Codeowner: Equipe Paiement</text>
      </g>

      <!-- Service B: Catalog -->
      <g transform="translate(400, 120)">
        <rect x="-80" y="-30" width="160" height="60" rx="10" fill="#31102f" stroke="#ec4899" stroke-width="2" filter="url(#glow)"/>
        <text x="0" y="-5" text-anchor="middle" fill="#ffffff" font-family="Outfit" font-weight="600" font-size="14">Product Catalog</text>
        <text x="0" y="15" text-anchor="middle" fill="#9ca3af" font-family="Inter" font-size="11">Codeowner: Equipe Catalogue</text>
      </g>

      <!-- Service C: Notification -->
      <g transform="translate(600, 200)">
        <rect x="-80" y="-30" width="160" height="60" rx="10" fill="#064e3b" stroke="#10b981" stroke-width="2" filter="url(#glow)"/>
        <text x="0" y="-5" text-anchor="middle" fill="#ffffff" font-family="Outfit" font-weight="600" font-size="14">Notification Dispatcher</text>
        <text x="0" y="15" text-anchor="middle" fill="#9ca3af" font-family="Inter" font-size="11">Codeowner: Notifications</text>
      </g>

      <!-- DB Node A -->
      <g transform="translate(200, 350)">
        <ellipse cx="0" cy="0" rx="60" ry="25" fill="#111827" stroke="#10b981" stroke-width="2"/>
        <text x="0" y="4" text-anchor="middle" fill="#34d399" font-family="JetBrains Mono" font-size="12">PostgreSQL (Payments)</text>
      </g>

      <!-- DB Node B -->
      <g transform="translate(400, 350)">
        <ellipse cx="0" cy="0" rx="60" ry="25" fill="#111827" stroke="#f59e0b" stroke-width="2"/>
        <text x="0" y="4" text-anchor="middle" fill="#fbbf24" font-family="JetBrains Mono" font-size="12">Elasticsearch (Products)</text>
      </g>
    </svg>
  `;
}

// Live Logs Stream Simulation
function initLiveLogs() {
  const body = document.getElementById('terminal-body');
  const clearBtn = document.getElementById('clear-terminal');

  const logMessages = [
    { type: 'info', text: '[POD-METRICS] srv-payment cpu: 4.2% memory: 142MB uptime: 99.98%' },
    { type: 'success', text: '[CI/CD] Build job #1402 completed for repository payment-service (SHA: 8f2a11b)' },
    { type: 'info', text: '[AUDIT] User "admin" requested feature flag update on NEW_PAYMENT_FLOW_V2 -> 50%' },
    { type: 'info', text: '[K8S-WATCH] Pod srv-catalog-7f8d9b-x421 status: RUNNING (1/1 ready)' },
    { type: 'success', text: '[PROMETHEUS] Scraping metrics endpoint GET /actuator/prometheus -> 200 OK (14ms)' }
  ];

  let msgIndex = 0;
  setInterval(() => {
    const msg = logMessages[msgIndex % logMessages.length];
    const div = document.createElement('div');
    div.className = `log-line ${msg.type}`;
    div.textContent = `[${new Date().toLocaleTimeString()}] ${msg.text}`;
    body.appendChild(div);
    body.scrollTop = body.scrollHeight;
    msgIndex++;
  }, 4000);

  clearBtn.addEventListener('click', () => {
    body.innerHTML = '<div class="log-line info">[SYSTEM] Terminal cleared. Listening for incoming telemetry...</div>';
  });
}

// Copilot RAG Chat Assistant
function initCopilot() {
  const input = document.getElementById('copilot-input');
  const sendBtn = document.getElementById('btn-copilot-send');
  const history = document.getElementById('chat-history');

  function appendMessage(text, isUser = false, sources = []) {
    const bubble = document.createElement('div');
    bubble.className = `chat-bubble ${isUser ? 'user' : 'bot'}`;
    
    let sourceHtml = '';
    if (sources.length > 0) {
      sourceHtml = `<div style="margin-top:8px; font-size:0.75rem; color:#9ca3af; border-top:1px solid rgba(255,255,255,0.1); padding-top:6px;">
        <strong>Sources cited:</strong> ${sources.map(s => `<code style="background:rgba(255,255,255,0.1); padding:2px 4px; border-radius:4px;">${s}</code>`).join(', ')}
      </div>`;
    }

    bubble.innerHTML = `
      <div class="bubble-avatar">${isUser ? '👤' : '🤖'}</div>
      <div class="bubble-content">
        ${text}
        ${sourceHtml}
      </div>
    `;
    history.appendChild(bubble);
    history.scrollTop = history.scrollHeight;
  }

  sendBtn.addEventListener('click', () => {
    const q = input.value.trim();
    if (!q) return;

    appendMessage(q, true);
    input.value = '';

    // Simulated RAG Pipeline response
    setTimeout(() => {
      if (q.toLowerCase().includes('payment') || q.toLowerCase().includes('refund')) {
        appendMessage(
          'The <strong>Payment Gateway Service</strong> (<code>srv-payment</code>) handles credit card charges and refund workflows. It exposes <code>POST /api/v1/payments/charge</code> and <code>POST /api/v1/payments/{id}/refund</code>. It is owned by <strong>Equipe Paiement</strong>.',
          false,
          ['payment-service/README.md', 'openapi-spec.yaml#L45-L88']
        );
      } else {
        appendMessage(
          'Based on the IDP catalog documentation, we currently track 3 active microservices: <strong>Payment Gateway</strong>, <strong>Product Catalog API</strong>, and <strong>Notification Dispatcher</strong>. All services publish health metrics to Prometheus.',
          false,
          ['architecture/catalog-index.json', 'ADR-004-idp-standards.md']
        );
      }
    }, 800);
  });
}
