<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false showAnotherWayIfPresent=true>
<!DOCTYPE html>
<html lang="en" class="dark">
<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Authentication &bull; Internal Developer Platform</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Geist:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700&family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
</head>
<body class="heroui-root">
    <div class="heroui-split-layout">
        <!-- Left Side: HeroUI Ambient Showcase Panel -->
        <div class="heroui-hero-panel">
            <div class="heroui-hero-glow"></div>
            <div class="heroui-hero-grid"></div>
            
            <div class="heroui-brand-top">
                <div class="heroui-logo-badge">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                        <polygon points="12 2 2 7 12 12 22 7 12 2"></polygon>
                        <polyline points="2 17 12 22 22 17"></polyline>
                        <polyline points="2 12 12 17 22 12"></polyline>
                    </svg>
                </div>
                <div class="heroui-brand-text">
                    <span class="heroui-brand-name">Nexus IDP</span>
                    <span class="heroui-brand-tagline">Internal Developer Platform</span>
                </div>
            </div>

            <div class="heroui-hero-body">
                <h2 class="heroui-hero-heading">
                    Accelerate your <span class="heroui-gradient-text">Cloud Native</span> Golden Paths.
                </h2>
                <p class="heroui-hero-desc">
                    Self-service microservices catalog, automated project scaffolding, instant canary rollouts, and real-time observability telemetry.
                </p>
                <div class="heroui-metric-chips">
                    <div class="heroui-metric-chip">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#006fee" stroke-width="2.5"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></svg>
                        <span>&lt; 5ms Flag Eval</span>
                    </div>
                    <div class="heroui-metric-chip">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#17c964" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>
                        <span>OIDC S256 PKCE</span>
                    </div>
                    <div class="heroui-metric-chip">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#7828c8" stroke-width="2.5"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path></svg>
                        <span>pgvector RAG</span>
                    </div>
                </div>
            </div>

            <div class="heroui-hero-footer">
                <div class="heroui-glass-card">
                    <p class="heroui-quote-text">
                        &ldquo;This platform transforms how engineering teams scaffold, deploy, and monitor distributed microservices across Kubernetes clusters.&rdquo;
                    </p>
                    <div class="heroui-quote-author">
                        <div class="author-avatar">SD</div>
                        <div>
                            <div class="author-name">Sofia Davis</div>
                            <div class="author-title">VP of Platform Engineering</div>
                        </div>
                    </div>
                </div>

                <div class="heroui-compliance-badge">
                    <span class="status-pulse-dot"></span>
                    <span>Keycloak 24 IAM &bull; Realm: <strong>${realm.name}</strong> &bull; PKCE S256</span>
                </div>
            </div>
        </div>

        <!-- Right Side: Authentication Card -->
        <div class="heroui-auth-panel">
            <div class="heroui-auth-container">
                <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                    <div class="heroui-alert heroui-alert-${message.type}">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="12" cy="12" r="10"></circle>
                            <line x1="12" y1="8" x2="12" y2="12"></line>
                            <line x1="12" y1="16" x2="12.01" y2="16"></line>
                        </svg>
                        <span>${kcSanitize(message.summary)?no_esc}</span>
                    </div>
                </#if>

                <#nested "form">
            </div>
        </div>
    </div>
</body>
</html>
</#macro>
