<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('totp'); section>
    <#if section = "form">
        <div class="heroui-auth-card">
            <div class="heroui-card-header">
                <h1 class="heroui-title">Two-Factor Auth</h1>
                <p class="heroui-subtitle">Enter the 6-digit TOTP verification code from your authenticator application</p>
            </div>

            <form id="kc-otp-login-form" class="heroui-form" action="${url.loginAction}" method="post">
                <div class="heroui-field">
                    <label for="otp" class="heroui-label">One-Time Security Code</label>
                    <input type="text" id="otp" name="otp" class="heroui-input" autofocus autocomplete="one-time-code" placeholder="123456" maxlength="6" style="letter-spacing: 0.25em; text-align:center; font-size: 1.25rem; font-family: var(--font-mono);" required />
                </div>

                <div class="heroui-action-row" style="margin-top: 8px;">
                    <button class="heroui-btn-primary" name="login" type="submit">
                        <span>Verify &amp; Continue</span>
                    </button>
                </div>

                <a href="${url.loginUrl}" class="heroui-btn-secondary" style="display:flex; align-items:center; justify-content:center; text-decoration:none; margin-top: 12px;">
                    Cancel
                </a>
            </form>
        </div>
    </#if>
</@layout.registrationLayout>
