import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminService, PlatformUser } from './admin.service';

describe('AdminService', () => {
  let service: AdminService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AdminService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  const user: PlatformUser = {
    id: 'usr-1',
    username: 'alice',
    email: 'alice@company.internal',
    role: 'TECH_LEAD',
    team: 'Equipe Paiement',
    keycloakSubject: 'sub-1',
    active: true,
  };

  it('loads users into the signal', () => {
    service.loadUsers();
    const request = httpMock.expectOne('http://localhost:8088/api/v1/admin/users');
    request.flush([user]);

    expect(service.usersSignal()).toEqual([user]);
    expect(service.loadingSignal()).toBe(false);
  });

  it('loads policies and the RBAC matrix', () => {
    const policy = {
      id: 'pol-1',
      name: 'Team scoping',
      resourceType: 'SERVICE',
      action: 'UPDATE',
      effect: 'DENY',
      requireSameTeam: true,
      minRole: null,
      environment: null,
      criticality: null,
      maxRolloutPercent: null,
      requireCorporateIp: false,
      priority: 10,
      enabled: true,
    };

    service.loadAll();
    httpMock.expectOne('http://localhost:8088/api/v1/admin/users').flush([user]);
    const policyRequest = httpMock.expectOne('http://localhost:8088/api/v1/admin/policies');
    policyRequest.flush([policy]);
    httpMock.expectOne('http://localhost:8088/api/v1/rbac/matrix').flush({ ADMIN: ['MANAGE_ADMIN'] });

    expect(service.policiesSignal()).toEqual([policy]);
    expect(service.matrixSignal()).toEqual({ ADMIN: ['MANAGE_ADMIN'] });
  });

  it('marks access denied on a 403 and surfaces the reason', () => {
    service.loadUsers();
    const request = httpMock.expectOne('http://localhost:8088/api/v1/admin/users');
    request.flush({ detail: 'nope' }, { status: 403, statusText: 'Forbidden' });

    expect(service.accessDeniedSignal()).toBe(true);
    expect(service.errorSignal()).toContain('ADMIN role required');
    expect(service.usersSignal()).toEqual([]);
  });

  it('updateUserRole patches the role and reloads users', () => {
    service.updateUserRole('usr-1', 'ADMIN', 'Platform').subscribe(result => {
      expect(result).toEqual({ ...user, role: 'ADMIN', team: 'Platform' });
    });
    const patch = httpMock.expectOne('http://localhost:8088/api/v1/admin/users/usr-1/role');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ role: 'ADMIN', team: 'Platform' });
    patch.flush({ ...user, role: 'ADMIN', team: 'Platform' });

    // The tap reloads the user list.
    httpMock.expectOne('http://localhost:8088/api/v1/admin/users').flush([user]);
  });

  it('simulate posts the request and stores the result', () => {
    const request = {
      userId: 'usr-1',
      resourceType: 'FEATURE_FLAG',
      action: 'ROLLOUT',
      resourceId: 'ff-1',
      rolloutPercent: 80,
    };

    service.simulate(request);
    const post = httpMock.expectOne('http://localhost:8088/api/v1/admin/policies/simulate');
    expect(post.request.body).toEqual(request);
    post.flush({
      granted: false,
      stage: 'ABAC',
      reason: 'ceiling',
      policyId: 'pol-30',
      subject: { id: 'usr-1', username: 'alice', role: 'TECH_LEAD', team: 'Equipe Paiement' },
      resource: { type: 'FEATURE_FLAG', id: 'ff-1', ownerTeam: 'Equipe Paiement', environment: 'PROD', criticality: 'STANDARD' },
      action: 'ROLLOUT',
    });

    expect(service.simulationSignal()?.granted).toBe(false);
    expect(service.simulationSignal()?.policyId).toBe('pol-30');
  });
});
