<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "form">
        <div class="heroui-auth-card">
            <div class="heroui-card-header">
                <h1 class="heroui-title">Update Password</h1>
                <p class="heroui-subtitle">You must update your credentials before accessing the IDP</p>
            </div>

            <form id="kc-passwd-update-form" class="heroui-form" action="${url.loginAction}" method="post">
                <div class="heroui-field">
                    <label for="password-new" class="heroui-label">New Password</label>
                    <input type="password" id="password-new" name="password-new" class="heroui-input" autofocus autocomplete="new-password" placeholder="••••••••••••" required />
                </div>

                <div class="heroui-field">
                    <label for="password-confirm" class="heroui-label">Confirm New Password</label>
                    <input type="password" id="password-confirm" name="password-confirm" class="heroui-input" autocomplete="new-password" placeholder="••••••••••••" required />
                </div>

                <div class="heroui-action-row" style="margin-top: 8px;">
                    <button class="heroui-btn-primary" type="submit">
                        <span>Save &amp; Proceed</span>
                    </button>
                </div>
            </form>
        </div>
    </#if>
</@layout.registrationLayout>
