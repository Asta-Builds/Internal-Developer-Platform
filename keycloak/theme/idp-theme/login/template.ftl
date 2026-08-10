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
    <link href="https://fonts.googleapis.com/css2?family=Geist:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
</head>
<body class="shadcn-root">
    <div class="shadcn-split-layout">
        <!-- Left Side: Hero Brand / Testimonial Panel (shadcn style) -->
        <div class="shadcn-hero-panel">
            <div class="shadcn-hero-pattern"></div>
            <div class="shadcn-brand-top">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="shadcn-logo-icon">
                    <path d="M12 2L2 7l10 5 10-5-10-5z"></path>
                    <path d="M2 17l10 5 10-5"></path>
                    <path d="M2 12l10 5 10-5"></path>
                </svg>
                <span class="shadcn-brand-name">Nexus IDP Inc.</span>
            </div>

            <div class="shadcn-hero-footer">
                <div class="shadcn-quote-card">
                    <p class="shadcn-quote-text">
                        &ldquo;This platform has completely transformed how our engineering teams scaffold, deploy, and monitor distributed microservices across multi-region Kubernetes clusters.&rdquo;
                    </p>
                    <div class="shadcn-quote-author">
                        <div class="author-name">Sofia Davis</div>
                        <div class="author-title">VP of Platform Engineering</div>
                    </div>
                </div>

                <div class="shadcn-compliance-badge">
                    <span class="status-dot"></span>
                    <span>Keycloak 24 IAM &bull; Realm: <strong>${realm.name}</strong> &bull; PKCE S256</span>
                </div>
            </div>
        </div>

        <!-- Right Side: Authentication Card -->
        <div class="shadcn-auth-panel">
            <div class="shadcn-auth-container">
                <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                    <div class="shadcn-alert shadcn-alert-${message.type}">
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
