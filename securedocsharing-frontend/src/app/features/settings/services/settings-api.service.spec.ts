import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SettingsApiService } from './settings-api.service';
import { WorkspacePolicyDto, WorkspacePolicyTarget } from '../interfaces/settings.interfaces';
import { environment } from '../../../../environments/environment';

describe('SettingsApiService — Policy Contract', () => {
  let service: SettingsApiService;
  let httpMock: HttpTestingController;

  const mockPolicy: WorkspacePolicyDto = {
    workspaceType: 'PERSONAL',
    externalSharingMode: 'OFF',
    discoverabilityMode: 'PRIVATE',
    allowAccessRequests: true,
    requireRequestReason: false,
    allowExternalAccessRequests: false,
    reviewerScopePolicy: 'RESOURCE_ADMINS_ONLY'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SettingsApiService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(SettingsApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('A. should request personal policy via /workspaces/personal/policies', () => {
    service.getWorkspacePolicy('personal').subscribe((res) => {
      expect(res).toEqual(mockPolicy);
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/workspaces/personal/policies`);
    expect(req.request.method).toBe('GET');
    req.flush(mockPolicy);
  });

  it('B. should request organization policy via /workspaces/{orgCode}/policies', () => {
    service.getWorkspacePolicy('ACME_CORP').subscribe((res) => {
      expect(res).toEqual(mockPolicy);
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/workspaces/ACME_CORP/policies`);
    expect(req.request.method).toBe('GET');
    req.flush(mockPolicy);
  });

  it('C. should update personal policy via PUT /workspaces/personal/policies', () => {
    service.updateWorkspacePolicy('personal', mockPolicy).subscribe((res) => {
      expect(res).toEqual(mockPolicy);
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/workspaces/personal/policies`);
    expect(req.request.method).toBe('PUT');
    req.flush(mockPolicy);
  });

  it('D. should update organization policy via PUT /workspaces/{orgCode}/policies', () => {
    service.updateWorkspacePolicy('ACME_CORP', mockPolicy).subscribe((res) => {
      expect(res).toEqual(mockPolicy);
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/v1/workspaces/ACME_CORP/policies`);
    expect(req.request.method).toBe('PUT');
    req.flush(mockPolicy);
  });

  it('E. should REJECT numeric workspace ID and never dispatch HTTP request', () => {
    expect(() => {
      service.getWorkspacePolicy(42 as unknown as WorkspacePolicyTarget);
    }).toThrowError(/Numeric ProjectWorkspace IDs must never be passed/);

    expect(() => {
      service.updateWorkspacePolicy(10 as unknown as WorkspacePolicyTarget, mockPolicy);
    }).toThrowError(/Numeric ProjectWorkspace IDs must never be passed/);

    httpMock.expectNone(`${environment.apiBaseUrl}/v1/workspaces/42/policies`);
    httpMock.expectNone(`${environment.apiBaseUrl}/v1/workspaces/10/policies`);
  });
});
