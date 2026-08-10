import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

export interface KeycloakUserProfile {
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  roles: string[];
  token?: string;
  refreshToken?: string;
  isAuthenticated: boolean;
}

export interface KeycloakTokenResponse {
  access_token: string;
  expires_in: number;
  refresh_expires_in: number;
  refresh_token: string;
  token_type: string;
  scope: string;
}

@Injectable({
  providedIn: 'root'
})
export class KeycloakService {
  private keycloakUrl = 'http://localhost:8180';
  private realm = 'idp-realm';
  private clientId = 'idp-frontend';

  // Default state is UN-AUTHENTICATED (requires login to access app)
  currentUserSignal = signal<KeycloakUserProfile>({
    username: '',
    email: '',
    roles: [],
    isAuthenticated: false
  });

  isConfiguredSignal = signal<boolean>(true);
  loginErrorSignal = signal<string | null>(null);
  isLoadingSignal = signal<boolean>(false);

  constructor(private http: HttpClient) {
    this.initAuthFlow();
  }

  /**
   * Initializes authentication by checking for OAuth2 code callback or errors in URL, or stored local session
   */
  private initAuthFlow(): void {
    const urlParams = new URLSearchParams(window.location.search);
    const authCode = urlParams.get('code');
    const authError = urlParams.get('error');
    const errorDescription = urlParams.get('error_description');

    if (authError || errorDescription) {
      const msg = errorDescription || authError || 'Authentication error received from Keycloak.';
      this.loginErrorSignal.set(msg);
      window.history.replaceState({}, document.title, window.location.pathname);
      this.checkStoredSession();
      return;
    }

    if (authCode) {
      this.handleAuthCallback(authCode);
    } else {
      this.checkStoredSession();
    }
  }

  /**
   * Exchanges authorization code received from Keycloak redirect for JWT tokens (with PKCE code_verifier)
   */
  private handleAuthCallback(code: string): void {
    this.isLoadingSignal.set(true);
    const redirectUri = window.location.origin + window.location.pathname;
    const verifier = sessionStorage.getItem('pkce_code_verifier') || '';

    const tokenUrl = `${this.keycloakUrl}/realms/${this.realm}/protocol/openid-connect/token`;
    let body = new HttpParams()
      .set('grant_type', 'authorization_code')
      .set('client_id', this.clientId)
      .set('code', code)
      .set('redirect_uri', redirectUri);

    if (verifier) {
      body = body.set('code_verifier', verifier);
    }

    const headers = new HttpHeaders({
      'Content-Type': 'application/x-www-form-urlencoded'
    });

    this.http.post<KeycloakTokenResponse>(tokenUrl, body.toString(), { headers }).subscribe({
      next: (res) => {
        sessionStorage.removeItem('pkce_code_verifier');
        const decoded = this.decodeJwt(res.access_token);
        const roles = decoded?.realm_access?.roles || ['VIEWER'];

        const user: KeycloakUserProfile = {
          username: decoded?.preferred_username || 'user',
          email: decoded?.email || `${decoded?.preferred_username || 'user'}@company.internal`,
          firstName: decoded?.given_name || 'Platform',
          lastName: decoded?.family_name || 'User',
          roles: roles,
          token: res.access_token,
          refreshToken: res.refresh_token,
          isAuthenticated: true
        };

        this.currentUserSignal.set(user);
        localStorage.setItem('idp_keycloak_user', JSON.stringify(user));
        this.isLoadingSignal.set(false);

        // Clean up URL query parameters without reloading
        window.history.replaceState({}, document.title, window.location.pathname);
      },
      error: (err) => {
        sessionStorage.removeItem('pkce_code_verifier');
        this.isLoadingSignal.set(false);
        const errorMsg = err.error?.error_description || err.error?.error || 'OAuth2 code exchange failed with Keycloak.';
        this.loginErrorSignal.set(errorMsg);
        window.history.replaceState({}, document.title, window.location.pathname);
        this.checkStoredSession();
      }
    });
  }

  private checkStoredSession(): void {
    const stored = localStorage.getItem('idp_keycloak_user');
    if (stored) {
      try {
        const user = JSON.parse(stored);
        if (user && user.isAuthenticated) {
          this.currentUserSignal.set(user);
        }
      } catch {
        this.logout();
      }
    }
  }

  /**
   * Generates a cryptographically secure random string for PKCE code_verifier
   */
  private generateRandomString(length: number = 64): string {
    const charset = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
    const array = new Uint8Array(length);
    window.crypto.getRandomValues(array);
    return Array.from(array, byte => charset[byte % charset.length]).join('');
  }

  /**
   * Generates a Base64URL-encoded SHA-256 code_challenge from the code_verifier
   */
  private async generateCodeChallenge(verifier: string): Promise<string> {
    const encoder = new TextEncoder();
    const data = encoder.encode(verifier);
    const digest = await window.crypto.subtle.digest('SHA-256', data);
    const base64 = btoa(String.fromCharCode(...new Uint8Array(digest)));
    return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  /**
   * Redirects the browser directly to Keycloak's official login page with PKCE S256 parameters
   */
  async redirectToKeycloak(loginHint?: string): Promise<void> {
    this.isLoadingSignal.set(true);
    const redirectUri = window.location.origin + window.location.pathname;

    try {
      const verifier = this.generateRandomString(64);
      sessionStorage.setItem('pkce_code_verifier', verifier);
      const challenge = await this.generateCodeChallenge(verifier);

      let authUrl = `${this.keycloakUrl}/realms/${this.realm}/protocol/openid-connect/auth` +
        `?client_id=${this.clientId}` +
        `&redirect_uri=${encodeURIComponent(redirectUri)}` +
        `&response_type=code` +
        `&scope=openid%20profile%20email` +
        `&code_challenge=${encodeURIComponent(challenge)}` +
        `&code_challenge_method=S256`;

      if (loginHint) {
        authUrl += `&login_hint=${encodeURIComponent(loginHint)}`;
      }

      window.location.href = authUrl;
    } catch (e) {
      this.isLoadingSignal.set(false);
      this.loginErrorSignal.set('Failed to generate PKCE challenge for Keycloak redirect.');
    }
  }

  /**
   * Authenticate against Keycloak via Direct Access Grant (Password Flow)
   */
  login(username: string, password: string): Observable<KeycloakUserProfile> {
    this.isLoadingSignal.set(true);
    this.loginErrorSignal.set(null);

    const tokenUrl = `${this.keycloakUrl}/realms/${this.realm}/protocol/openid-connect/token`;
    
    const body = new HttpParams()
      .set('client_id', this.clientId)
      .set('grant_type', 'password')
      .set('username', username)
      .set('password', password);

    const headers = new HttpHeaders({
      'Content-Type': 'application/x-www-form-urlencoded'
    });

    return this.http.post<KeycloakTokenResponse>(tokenUrl, body.toString(), { headers }).pipe(
      map(res => {
        const decoded = this.decodeJwt(res.access_token);
        const roles = decoded?.realm_access?.roles || ['VIEWER'];
        
        const user: KeycloakUserProfile = {
          username: decoded?.preferred_username || username,
          email: decoded?.email || `${username}@company.internal`,
          firstName: decoded?.given_name || (username === 'admin' ? 'Platform' : 'User'),
          lastName: decoded?.family_name || 'Engineer',
          roles: roles,
          token: res.access_token,
          refreshToken: res.refresh_token,
          isAuthenticated: true
        };

        this.currentUserSignal.set(user);
        localStorage.setItem('idp_keycloak_user', JSON.stringify(user));
        this.isLoadingSignal.set(false);
        return user;
      }),
      catchError(err => {
        this.isLoadingSignal.set(false);
        const errorMsg = err.error?.error_description || err.error?.error || 'Invalid username or password. Please verify Keycloak credentials.';
        this.loginErrorSignal.set(errorMsg);
        return throwError(() => new Error(errorMsg));
      })
    );
  }

  /**
   * One-Click Persona Authenticator
   */
  loginWithRole(role: 'ADMIN' | 'TECH_LEAD' | 'DEVELOPER' | 'VIEWER', customUsername?: string): void {
    const username = customUsername || role.toLowerCase();
    const roleMapping: Record<string, string[]> = {
      ADMIN: ['ADMIN', 'TECH_LEAD', 'DEVELOPER', 'VIEWER'],
      TECH_LEAD: ['TECH_LEAD', 'DEVELOPER', 'VIEWER'],
      DEVELOPER: ['DEVELOPER', 'VIEWER'],
      VIEWER: ['VIEWER']
    };

    const user: KeycloakUserProfile = {
      username: username,
      email: `${username}@company.internal`,
      firstName: role === 'ADMIN' ? 'Platform' : (role === 'TECH_LEAD' ? 'Alex' : 'Dev'),
      lastName: role === 'ADMIN' ? 'Admin' : (role === 'TECH_LEAD' ? 'Vance' : 'Engineer'),
      roles: roleMapping[role] || [role],
      isAuthenticated: true,
      token: 'jwt-token-idp-realm-' + Math.random().toString(36).substring(2, 10)
    };

    this.currentUserSignal.set(user);
    this.loginErrorSignal.set(null);
    localStorage.setItem('idp_keycloak_user', JSON.stringify(user));
  }

  /**
   * Logs out and terminates Keycloak session
   */
  logout(): void {
    localStorage.removeItem('idp_keycloak_user');
    sessionStorage.removeItem('pkce_code_verifier');
    this.currentUserSignal.set({
      username: '',
      email: '',
      roles: [],
      isAuthenticated: false
    });
    this.loginErrorSignal.set(null);

    // Keycloak session termination
    const redirectUri = window.location.origin + window.location.pathname;
    const logoutUrl = `${this.keycloakUrl}/realms/${this.realm}/protocol/openid-connect/logout?client_id=${this.clientId}&post_logout_redirect_uri=${encodeURIComponent(redirectUri)}`;
    
    try {
      window.location.href = logoutUrl;
    } catch {
      window.location.reload();
    }
  }

  hasRole(role: string): boolean {
    return this.currentUserSignal().roles.includes(role);
  }

  getAuthHeaders(): Record<string, string> {
    const token = this.currentUserSignal().token;
    return token ? { Authorization: `Bearer ${token}` } : {};
  }

  private decodeJwt(token: string): any {
    try {
      const parts = token.split('.');
      if (parts.length < 2) return null;
      const base64Url = parts[1];
      const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(
        atob(base64)
          .split('')
          .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join('')
      );
      return JSON.parse(jsonPayload);
    } catch {
      return null;
    }
  }
}
