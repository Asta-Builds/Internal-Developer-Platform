import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, catchError, of, tap, throwError } from 'rxjs';

export type PlatformRole = 'ADMIN' | 'TECH_LEAD' | 'DEVELOPER' | 'VIEWER';

export interface PlatformUser {
  id: string;
  username: string;
  email: string;
  role: PlatformRole;
  team: string | null;
  /** Bound on first login; null means this account has never signed in. */
  keycloakSubject: string | null;
  active: boolean;
}

export interface RolePermission {
  id: string;
  role: PlatformRole;
  resourceType: string;
  action: string;
  description?: string;
}

export interface AbacPolicy {
  id: string;
  name: string;
  description?: string;
  resourceType: string;
  action: string;
  effect: 'ALLOW' | 'DENY';
  requireSameTeam: boolean;
  minRole: PlatformRole | null;
  environment: string | null;
  criticality: string | null;
  maxRolloutPercent: number | null;
  requireCorporateIp: boolean;
  priority: number;
  enabled: boolean;
}

export interface SimulationResult {
  granted: boolean;
  stage: 'AUTHENTICATION' | 'RBAC' | 'ABAC';
  reason: string;
  policyId: string | null;
  subject: { id: string; username: string; role: string; team: string };
  resource: { type: string; id: string; ownerTeam: string; environment: string; criticality: string };
  action: string;
}

/**
 * Client for the platform's own authorization administration API.
 *
 * <p>Every call here edits platform tables — Keycloak holds no permission data and is
 * never contacted. All endpoints require ADMIN, so a 403 is an expected outcome for
 * lesser roles and is surfaced rather than swallowed.
 */
@Injectable({ providedIn: 'root' })
export class AdminService {

  private http = inject(HttpClient);
  private getBaseUrl(): string {
    if (typeof window !== 'undefined') {
      const hostname = window.location.hostname;
      if (hostname && hostname !== 'localhost' && hostname !== '127.0.0.1') {
        return '/api/v1';
      }
    }
    return 'http://localhost:8088/api/v1';
  }
  private baseUrl = this.getBaseUrl();

  usersSignal = signal<PlatformUser[]>([]);
  policiesSignal = signal<AbacPolicy[]>([]);
  matrixSignal = signal<Record<string, string[]>>({});
  simulationSignal = signal<SimulationResult | null>(null);

  /** Set when the API refuses the current user; drives the "ADMIN required" notice. */
  accessDeniedSignal = signal<boolean>(false);
  errorSignal = signal<string | null>(null);
  loadingSignal = signal<boolean>(false);

  readonly resourceTypes = [
    'SERVICE', 'SCAFFOLD', 'FEATURE_FLAG', 'OBSERVABILITY',
    'AUDIT_LOG', 'COPILOT', 'GITHUB', 'DEVOPS', 'ADMIN'
  ];

  readonly actions = [
    'READ', 'CREATE', 'UPDATE', 'DELETE',
    'ROLLOUT', 'STREAM', 'INGEST', 'EXECUTE', 'MANAGE'
  ];

  readonly roles: PlatformRole[] = ['ADMIN', 'TECH_LEAD', 'DEVELOPER', 'VIEWER'];

  /** Loads everything the governance screen renders. */
  loadAll(): void {
    this.loadUsers();
    this.loadPolicies();
    this.loadMatrix();
  }

  loadUsers(): void {
    this.loadingSignal.set(true);
    this.http.get<PlatformUser[]>(`${this.baseUrl}/admin/users`).pipe(
      catchError(err => this.handle(err, []))
    ).subscribe(users => {
      this.usersSignal.set(users);
      this.loadingSignal.set(false);
    });
  }

  loadPolicies(): void {
    this.http.get<AbacPolicy[]>(`${this.baseUrl}/admin/policies`).pipe(
      catchError(err => this.handle(err, []))
    ).subscribe(policies => this.policiesSignal.set(policies));
  }

  /** The live RBAC matrix, readable by any authenticated user. */
  loadMatrix(): void {
    this.http.get<Record<string, string[]>>(`${this.baseUrl}/rbac/matrix`).pipe(
      catchError(() => of({} as Record<string, string[]>))
    ).subscribe(matrix => this.matrixSignal.set(matrix));
  }

  updateUserRole(id: string, role: PlatformRole, team: string | null): Observable<PlatformUser> {
    return this.http.patch<PlatformUser>(`${this.baseUrl}/admin/users/${id}/role`, { role, team }).pipe(
      tap(() => {
        this.errorSignal.set(null);
        this.loadUsers();
      }),
      catchError(err => this.fail(err))
    );
  }

  updateUserStatus(id: string, active: boolean): Observable<PlatformUser> {
    return this.http.patch<PlatformUser>(`${this.baseUrl}/admin/users/${id}/status`, { active }).pipe(
      tap(() => this.loadUsers()),
      catchError(err => this.fail(err))
    );
  }

  grantPermission(role: PlatformRole, resourceType: string, action: string): Observable<RolePermission> {
    return this.http.post<RolePermission>(
      `${this.baseUrl}/admin/roles/${role}/permissions`,
      { resourceType, action, description: `Granted from the admin console` }
    ).pipe(
      tap(() => {
        this.errorSignal.set(null);
        this.loadMatrix();
      }),
      catchError(err => this.fail(err))
    );
  }

  revokePermission(role: PlatformRole, resourceType: string, action: string): Observable<void> {
    const params = new HttpParams().set('resourceType', resourceType).set('action', action);
    return this.http.delete<void>(`${this.baseUrl}/admin/roles/${role}/permissions`, { params }).pipe(
      tap(() => this.loadMatrix()),
      catchError(err => this.fail(err))
    );
  }

  togglePolicy(id: string, enabled: boolean): Observable<AbacPolicy> {
    return this.http.patch<AbacPolicy>(`${this.baseUrl}/admin/policies/${id}`, { enabled }).pipe(
      tap(() => this.loadPolicies()),
      catchError(err => this.fail(err))
    );
  }

  /**
   * Dry-runs a decision for a stored user. The subject is identified by id and the
   * resource attributes are read server-side, so nothing here is caller-asserted.
   */
  simulate(request: {
    userId: string;
    resourceType: string;
    action: string;
    resourceId?: string;
    rolloutPercent?: number;
  }): void {
    this.http.post<SimulationResult>(`${this.baseUrl}/admin/policies/simulate`, request).pipe(
      catchError(err => {
        this.handle(err, null);
        return of(null);
      })
    ).subscribe(result => this.simulationSignal.set(result));
  }

  private handle<T>(err: HttpErrorResponse, fallback: T): Observable<T> {
    this.loadingSignal.set(false);
    if (err.status === 403) {
      this.accessDeniedSignal.set(true);
      this.errorSignal.set('ADMIN role required. This refusal was recorded in the audit log.');
    } else {
      this.errorSignal.set(err.error?.detail || err.message || 'Request failed');
    }
    return of(fallback);
  }

  private fail(err: HttpErrorResponse): Observable<never> {
    this.handle(err, null);
    return throwError(() => err);
  }
}
