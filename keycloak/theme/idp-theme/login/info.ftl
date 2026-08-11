<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "form">
        <div class="heroui-auth-card">
            <div class="heroui-card-header">
                <div style="width: 48px; height: 48px; border-radius: var(--heroui-radius-full); background: var(--heroui-primary-50); border: 1px solid rgba(0,111,238,0.35); display: flex; align-items: center; justify-content: center; margin: 0 auto 12px auto; color: var(--heroui-primary);">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="10"></circle>
                        <line x1="12" y1="16" x2="12" y2="12"></line>
                        <line x1="12" y1="8" x2="12.01" y2="8"></line>
                    </svg>
                </div>
                <h1 class="heroui-title">Information</h1>
                <p class="heroui-subtitle">${message.summary}</p>
            </div>

            <#if actionUri??>
                <a href="${actionUri}" class="heroui-btn-primary" style="display:flex; align-items:center; justify-content:center; text-decoration:none;">
                    Proceed
                </a>
            <#elseif client?? && client.baseUrl?has_content>
                <a href="${client.baseUrl}" class="heroui-btn-primary" style="display:flex; align-items:center; justify-content:center; text-decoration:none;">
                    Return to Application
                </a>
            <#else>
                <a href="${url.loginUrl}" class="heroui-btn-primary" style="display:flex; align-items:center; justify-content:center; text-decoration:none;">
                    Back to Sign In
                </a>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>
