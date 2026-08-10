<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "form">
        <div class="shadcn-auth-card">
            <div class="shadcn-card-header">
                <h1 class="shadcn-title">Welcome back</h1>
                <p class="shadcn-subtitle">Enter your corporate credentials below to access the Internal Developer Platform</p>
            </div>

            <#if realm.password>
                <form id="kc-form-login" class="shadcn-form" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                    <div class="shadcn-field">
                        <label for="username" class="shadcn-label">Email or Username</label>
                        <input tabindex="1" id="username" class="shadcn-input" name="username" value="${(login.username!'')}" type="text" autofocus autocomplete="username" placeholder="name@example.com or admin" required />
                    </div>

                    <div class="shadcn-field">
                        <div class="shadcn-label-row">
                            <label for="password" class="shadcn-label">Password</label>
                            <span class="shadcn-sublink">Keycloak IAM 24</span>
                        </div>
                        <div class="shadcn-input-wrapper">
                            <input tabindex="2" id="password" class="shadcn-input" name="password" type="password" autocomplete="current-password" placeholder="••••••••••••" required />
                            <button type="button" class="shadcn-eye-btn" onclick="togglePasswordVisibility()" title="Toggle password visibility">
                                <svg id="eye-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                                    <circle cx="12" cy="12" r="3"></circle>
                                </svg>
                            </button>
                        </div>
                    </div>

                    <div class="shadcn-checkbox-row">
                        <#if realm.rememberMe && !usernameHidden??>
                            <label class="shadcn-checkbox-label">
                                <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if>>
                                <span>Remember me</span>
                            </label>
                        </#if>
                    </div>

                    <div class="shadcn-action-row">
                        <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                        <button tabindex="4" class="shadcn-btn-primary" name="login" id="kc-login" type="submit">
                            Sign In with Keycloak
                        </button>
                    </div>
                </form>
            </#if>

            <!-- shadcn Divider -->
            <div class="shadcn-divider">
                <span class="shadcn-divider-line"></span>
                <span class="shadcn-divider-text">Or continue with persona</span>
                <span class="shadcn-divider-line"></span>
            </div>

            <!-- Personas Cards (shadcn Outline Buttons Style) -->
            <div class="shadcn-personas-grid">
                <button type="button" class="shadcn-persona-btn" onclick="fillPersona('admin', 'adminpassword')">
                    <div class="persona-btn-top">
                        <span class="persona-name">Platform Admin</span>
                        <span class="shadcn-badge badge-admin">ADMIN</span>
                    </div>
                    <div class="persona-desc">admin / adminpassword &bull; Full Control</div>
                </button>

                <button type="button" class="shadcn-persona-btn" onclick="fillPersona('tech_lead', 'leadpassword')">
                    <div class="persona-btn-top">
                        <span class="persona-name">Tech Lead</span>
                        <span class="shadcn-badge badge-lead">TECH_LEAD</span>
                    </div>
                    <div class="persona-desc">tech_lead / leadpassword &bull; Canaries &amp; Flags</div>
                </button>

                <button type="button" class="shadcn-persona-btn" onclick="fillPersona('developer', 'devpassword')">
                    <div class="persona-btn-top">
                        <span class="persona-name">Developer</span>
                        <span class="shadcn-badge badge-dev">DEVELOPER</span>
                    </div>
                    <div class="persona-desc">developer / devpassword &bull; Microservices</div>
                </button>

                <button type="button" class="shadcn-persona-btn" onclick="fillPersona('viewer', 'viewerpassword')">
                    <div class="persona-btn-top">
                        <span class="persona-name">Viewer</span>
                        <span class="shadcn-badge badge-viewer">VIEWER</span>
                    </div>
                    <div class="persona-desc">viewer / viewerpassword &bull; Read-Only</div>
                </button>
            </div>

            <p class="shadcn-terms">
                By clicking continue, you agree to our 
                <a href="#" style="color:var(--zinc-300); text-decoration:underline;">Terms of Service</a> and 
                <a href="#" style="color:var(--zinc-300); text-decoration:underline;">Privacy Policy</a>.
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
