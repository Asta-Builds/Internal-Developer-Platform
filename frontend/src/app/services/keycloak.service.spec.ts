import { TestBed } from '@angular/core/testing';
import { provideHttpClient, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { KeycloakService, KeycloakUserProfile } from './keycloak.service';

describe('KeycloakService', () => {
  let service: KeycloakService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    window.history.replaceState({}, document.title, window.location.pathname);

    TestBed.configureTestingModule({
      providers: [KeycloakService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(KeycloakService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('starts unauthenticated when no session is stored', () => {
    expect(service.currentUserSignal().isAuthenticated).toBe(false);
    expect(service.currentUserSignal().roles).toEqual([]);
  });

  it('restores a stored session from localStorage', () => {
    localStorage.setItem(
      'idp_keycloak_user',
      JSON.stringify({
        username: 'alice',
        email: 'alice@company.internal',
        roles: ['TECH_LEAD'],
        token: 'tok',
        isAuthenticated: true,
      })
    );

    const restored = new KeycloakService(TestBed.inject(HttpClient));

    expect(restored.currentUserSignal().isAuthenticated).toBe(true);
    expect(restored.currentUserSignal().roles).toEqual(['TECH_LEAD']);
  });

  it('login() stores the token and builds the profile', () => {
    const captured: KeycloakUserProfile[] = [];
    service.login('alice', 'secret').subscribe(profile => captured.push(profile));

    const request = httpMock.expectOne('http://localhost:8180/realms/idp-realm/protocol/openid-connect/token');
    expect(request.request.method).toBe('POST');

    request.flush({
      access_token: 'eyJhbGciOiJSUzI1NiJ9.eyJwcmVmZXJyZWRfdXNlcm5hbWUiOiJhbGljZSIsImVtYWlsIjoiYWxpY2VAeC5pbyIsInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJERVZFTE9QRVIiXX19.signature',
      refresh_token: 'rt',
      expires_in: 300,
      refresh_expires_in: 1800,
      token_type: 'Bearer',
      scope: 'openid',
    });

    expect(captured).toHaveLength(1);
    expect(captured[0].isAuthenticated).toBe(true);
    expect(captured[0].roles).toContain('DEVELOPER');
    expect(captured[0].token).toContain('signature');

    expect(service.currentUserSignal().isAuthenticated).toBe(true);
    expect(JSON.parse(localStorage.getItem('idp_keycloak_user')!).roles).toContain('DEVELOPER');
  });

  it('hasRole() reflects the current profile roles', () => {
    service.currentUserSignal.set({
      username: 'bob',
      email: 'b@x.io',
      roles: ['ADMIN'],
      isAuthenticated: true,
    });

    expect(service.hasRole('ADMIN')).toBe(true);
    expect(service.hasRole('VIEWER')).toBe(false);
  });

  it('getAuthHeaders() attaches the bearer token when present', () => {
    expect(service.getAuthHeaders()).toEqual({});

    service.currentUserSignal.set({
      username: 'bob',
      email: 'b@x.io',
      roles: [],
      token: 'tok-123',
      isAuthenticated: true,
    });

    expect(service.getAuthHeaders()).toEqual({ Authorization: 'Bearer tok-123' });
  });

  it('logout() clears the session', () => {
    service.currentUserSignal.set({
      username: 'bob',
      email: 'b@x.io',
      roles: ['VIEWER'],
      token: 'tok',
      isAuthenticated: true,
    });
    localStorage.setItem('idp_keycloak_user', '{"isAuthenticated":true}');

    service.logout();

    expect(service.currentUserSignal().isAuthenticated).toBe(false);
    expect(localStorage.getItem('idp_keycloak_user')).toBeNull();
  });
});
