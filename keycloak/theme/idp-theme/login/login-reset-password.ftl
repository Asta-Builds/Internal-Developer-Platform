<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true displayMessage=!messagesPerField.existsError('username'); section>
    <#if section = "form">
        <div class="heroui-auth-card">
            <div class="heroui-card-header">
                <h1 class="heroui-title">Reset Password</h1>
                <p class="heroui-subtitle">Enter your corporate email address to receive password reset instructions</p>
            </div>

            <form id="kc-reset-password-form" class="heroui-form" action="${url.loginAction}" method="post">
                <div class="heroui-field">
                    <label for="username" class="heroui-label">Email or Username</label>
                    <input type="text" id="username" name="username" class="heroui-input" autofocus value="${(auth.attemptedUsername!'')}" placeholder="name@example.com" required />
                </div>

                <div class="heroui-action-row" style="margin-top: 8px;">
                    <button class="heroui-btn-primary" type="submit">
                        <span>Send Recovery Link</span>
                    </button>
                </div>

                <div class="heroui-divider">
                    <span class="heroui-divider-line"></span>
                    <span class="heroui-divider-text">Back to safety</span>
                    <span class="heroui-divider-line"></span>
                </div>

                <a href="${url.loginUrl}" class="heroui-btn-secondary" style="display:flex; align-items:center; justify-content:center; text-decoration:none;">
                    Return to Sign In
                </a>
            </form>
        </div>
    </#if>
</@layout.registrationLayout>
