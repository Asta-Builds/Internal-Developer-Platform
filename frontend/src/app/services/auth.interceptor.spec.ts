import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { KeycloakService } from './keycloak.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let keycloak: KeycloakService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        KeycloakService,
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    keycloak = TestBed.inject(KeycloakService);
    keycloak.currentUserSignal.set({
      username: 'alice',
      email: 'a@x.io',
      roles: ['DEVELOPER'],
      token: 'access-token-123',
      isAuthenticated: true,
    });
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('attaches the bearer token to backend API calls', () => {
    http.get('http://localhost:8088/api/v1/catalog/services').subscribe();

    const request = httpMock.expectOne('http://localhost:8088/api/v1/catalog/services');
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-token-123');
  });

  it('attaches the bearer token to relative /api/ calls', () => {
    http.get('/api/v1/health').subscribe();

    const request = httpMock.expectOne('/api/v1/health');
    expect(request.request.headers.get('Authorization')).toBe('Bearer access-token-123');
  });

  it('does not leak the token to foreign origins', () => {
    http.get('https://third-party.example.com/collect').subscribe();

    const request = httpMock.expectOne('https://third-party.example.com/collect');
    expect(request.request.headers.has('Authorization')).toBe(false);
  });

  it('does not send a token when the user is signed out', () => {
    keycloak.currentUserSignal.set({
      username: '',
      email: '',
      roles: [],
      isAuthenticated: false,
    });

    http.get('http://localhost:8088/api/v1/catalog/services').subscribe();

    const request = httpMock.expectOne('http://localhost:8088/api/v1/catalog/services');
    expect(request.request.headers.has('Authorization')).toBe(false);
  });

  it('logs out when a backend call is refused with 401', () => {
    const logout = jest.spyOn(keycloak, 'logout');

    http.get('http://localhost:8088/api/v1/audit').subscribe({ error: () => undefined });
    const request = httpMock.expectOne('http://localhost:8088/api/v1/audit');
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(logout).toHaveBeenCalled();
  });

  it('does not log out on a 401 from a foreign origin', () => {
    const logout = jest.spyOn(keycloak, 'logout');

    http.get('https://third-party.example.com/collect').subscribe({ error: () => undefined });
    const request = httpMock.expectOne('https://third-party.example.com/collect');
    request.flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(logout).not.toHaveBeenCalled();
  });
});
