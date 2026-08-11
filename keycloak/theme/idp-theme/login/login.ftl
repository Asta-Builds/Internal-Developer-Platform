<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "form">
        <div class="heroui-auth-card">
            <div class="heroui-card-header">
                <h1 class="heroui-title">Welcome back</h1>
                <p class="heroui-subtitle">Enter your credentials below to access the Internal Developer Platform</p>
            </div>

            <#if realm.password>
                <form id="kc-form-login" class="heroui-form" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                    <div class="heroui-field">
                        <label for="username" class="heroui-label">Email or Username</label>
                        <input tabindex="1" id="username" class="heroui-input" name="username" value="${(login.username!'')}" type="text" autofocus autocomplete="username" placeholder="name@example.com or admin" required />
                    </div>

                    <div class="heroui-field">
                        <div class="heroui-label-row">
                            <label for="password" class="heroui-label">Password</label>
                            <#if realm.resetPasswordAllowed>
                                <a tabindex="5" href="${url.loginResetCredentialsUrl}" class="heroui-sublink">Forgot password?</a>
                            <#else>
                                <span class="heroui-sublink" style="color:var(--heroui-foreground-subtle);">Keycloak 24</span>
                            </#if>
                        </div>
                        <div class="heroui-input-wrapper">
                            <input tabindex="2" id="password" class="heroui-input" name="password" type="password" autocomplete="current-password" placeholder="••••••••••••" required />
                            <button type="button" class="heroui-eye-btn" onclick="togglePasswordVisibility()" title="Toggle password visibility">
                                <svg id="eye-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                                    <circle cx="12" cy="12" r="3"></circle>
                                </svg>
                            </button>
                        </div>
                    </div>

                    <div class="heroui-checkbox-row">
                        <#if realm.rememberMe && !usernameHidden??>
                            <label class="heroui-checkbox-label">
                                <input tabindex="3" id="rememberMe" name="rememberMe" class="heroui-checkbox" type="checkbox" <#if login.rememberMe??>checked</#if>>
                                <span>Remember me</span>
                            </label>
                        </#if>
                    </div>

                    <div class="heroui-action-row">
                        <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                        <button tabindex="4" class="heroui-btn-primary" name="login" id="kc-login" type="submit">
                            <span>Sign In with Keycloak</span>
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                                <line x1="5" y1="12" x2="19" y2="12"></line>
                                <polyline points="12 5 19 12 12 19"></polyline>
                            </svg>
                        </button>
                    </div>
                </form>
            </#if>

            <!-- HeroUI Divider -->
            <div class="heroui-divider">
                <span class="heroui-divider-line"></span>
                <span class="heroui-divider-text">Or test with persona</span>
                <span class="heroui-divider-line"></span>
            </div>

            <!-- Personas Cards (HeroUI Interactive Cards) -->
            <div class="heroui-personas-grid">
                <button type="button" class="heroui-persona-card" onclick="fillPersona('admin', 'adminpassword')">
                    <div class="persona-card-header">
                        <span class="persona-title">Platform Admin</span>
                        <span class="heroui-chip chip-admin">ADMIN</span>
                    </div>
                    <div class="persona-meta">admin / adminpassword &bull; Full Access</div>
                </button>

                <button type="button" class="heroui-persona-card" onclick="fillPersona('tech_lead', 'leadpassword')">
                    <div class="persona-card-header">
                        <span class="persona-title">Tech Lead</span>
                        <span class="heroui-chip chip-lead">TECH_LEAD</span>
                    </div>
                    <div class="persona-meta">tech_lead / leadpassword &bull; Canaries &amp; Flags</div>
                </button>

                <button type="button" class="heroui-persona-card" onclick="fillPersona('developer', 'devpassword')">
                    <div class="persona-card-header">
                        <span class="persona-title">Developer</span>
                        <span class="heroui-chip chip-dev">DEVELOPER</span>
                    </div>
                    <div class="persona-meta">developer / devpassword &bull; Scaffolding</div>
                </button>

                <button type="button" class="heroui-persona-card" onclick="fillPersona('viewer', 'viewerpassword')">
                    <div class="persona-card-header">
                        <span class="persona-title">Viewer</span>
                        <span class="heroui-chip chip-viewer">VIEWER</span>
                    </div>
                    <div class="persona-meta">viewer / viewerpassword &bull; Read-Only</div>
                </button>
            </div>

            <p class="heroui-legal-text">
                Protected by Keycloak IAM PKCE OAuth2.0 &bull;
                <a href="#">Security Policies</a>
            </p>
        </div>

        <script>
            function fillPersona(username, password) {
                const userField = document.getElementById('username');
                const passField = document.getElementById('password');
                if (userField && passField) {
                    userField.value = username;
                    passField.value = password;
                    userField.focus();
                }
            }

            function togglePasswordVisibility() {
                const passField = document.getElementById('password');
                const eyeIcon = document.getElementById('eye-icon');
                if (passField) {
                    if (passField.type === 'password') {
                        passField.type = 'text';
                        eyeIcon.innerHTML = '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line>';
                    } else {
                        passField.type = 'password';
                        eyeIcon.innerHTML = '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle>';
                    }
                }
            }
        </script>
    </#if>
</@layout.registrationLayout>
