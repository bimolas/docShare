import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TeamDelegationService } from './team-delegation.service';
import {
  TeamAccessBoundaryDto,
  TeamMemberAccessDto,
  GrantMemberAccessRequestDto,
  UpdateMemberAccessRequestDto,
  ScopedResourceNodeDto,
} from '../models/team-delegation.models';
import { environment } from '../../../../environments/environment';

describe('TeamDelegationService', () => {
  let service: TeamDelegationService;
  let httpMock: HttpTestingController;

  const orgCode = 'ORG-TEST';
  const teamId = 12;
  const baseUrl = `${environment.apiBaseUrl}/v1/organizations/${encodeURIComponent(orgCode)}/teams/${teamId}/member-access`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        TeamDelegationService,
      ],
    });
    service = TestBed.inject(TeamDelegationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch team access boundary', () => {
    const mockBoundary: TeamAccessBoundaryDto = {
      teamId: 12,
      teamName: 'Core Team',
      workspaceId: 1,
      workspaceName: 'Engineering Workspace',
      rootFolderId: 55,
      rootFolderName: 'Engineering',
      rootFolderPath: '/Engineering',
      permissionLevel: 'EDIT',
      permissionType: 'BOUNDARY',
      status: 'ACTIVE',
      assignedAt: '2026-09-08T10:00:00Z',
    };

    service.getBoundary(orgCode, teamId).subscribe((boundary) => {
      expect(boundary).toEqual(mockBoundary);
      expect(boundary.permissionLevel).toBe('EDIT');
    });

    const req = httpMock.expectOne(`${baseUrl}/boundary`);
    expect(req.request.method).toBe('GET');
    req.flush(mockBoundary);
  });

  it('should fetch member access list', () => {
    const mockAccessList: TeamMemberAccessDto[] = [
      {
        permissionId: 201,
        teamId: 12,
        teamName: 'Core Team',
        userEmail: 'alice@example.com',
        userName: 'Alice Smith',
        resourceId: 300,
        resourceType: 'DOCUMENT',
        resourceName: 'Specs.docx',
        resourcePath: '/Engineering/Specs.docx',
        permissionLevel: 'VIEW',
        isMigrated: false,
        grantedAt: '2026-09-08T12:00:00Z',
      },
    ];

    service.getMemberAccessList(orgCode, teamId).subscribe((list) => {
      expect(list.length).toBe(1);
      expect(list[0].userEmail).toBe('alice@example.com');
      expect(list[0].permissionLevel).toBe('VIEW');
    });

    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('GET');
    req.flush(mockAccessList);
  });

  it('should grant member access within boundary and ceiling', () => {
    const request: GrantMemberAccessRequestDto = {
      userEmail: 'alice@example.com',
      documentId: 300,
      permissionLevel: 'VIEW',
    };

    const mockResponse: TeamMemberAccessDto = {
      permissionId: 201,
      teamId: 12,
      teamName: 'Core Team',
      userEmail: 'alice@example.com',
      userName: 'Alice Smith',
      resourceId: 300,
      resourceType: 'DOCUMENT',
      resourceName: 'Specs.docx',
      resourcePath: '/Engineering/Specs.docx',
      permissionLevel: 'VIEW',
      isMigrated: false,
      grantedAt: '2026-09-08T12:00:00Z',
    };

    service.grantMemberAccess(orgCode, teamId, request).subscribe((res) => {
      expect(res.permissionId).toBe(201);
      expect(res.permissionLevel).toBe('VIEW');
    });

    const req = httpMock.expectOne(baseUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockResponse);
  });

  it('should update member access level', () => {
    const permissionId = 201;
    const request: UpdateMemberAccessRequestDto = {
      permissionLevel: 'DOWNLOAD',
    };

    const mockResponse: TeamMemberAccessDto = {
      permissionId: 201,
      teamId: 12,
      teamName: 'Core Team',
      userEmail: 'alice@example.com',
      userName: 'Alice Smith',
      resourceId: 300,
      resourceType: 'DOCUMENT',
      resourceName: 'Specs.docx',
      resourcePath: '/Engineering/Specs.docx',
      permissionLevel: 'DOWNLOAD',
      isMigrated: false,
      grantedAt: '2026-09-08T12:00:00Z',
    };

    service.updateMemberAccess(orgCode, teamId, permissionId, request).subscribe((res) => {
      expect(res.permissionLevel).toBe('DOWNLOAD');
    });

    const req = httpMock.expectOne(`${baseUrl}/${permissionId}`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(mockResponse);
  });

  it('should revoke member access', () => {
    const permissionId = 201;

    service.revokeMemberAccess(orgCode, teamId, permissionId).subscribe(() => {
      expect(true).toBeTrue();
    });

    const req = httpMock.expectOne(`${baseUrl}/${permissionId}`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should get scoped resources tree within boundary', () => {
    const mockTree: ScopedResourceNodeDto = {
      id: 55,
      name: 'Engineering Workspace',
      type: 'FOLDER',
      permissionCeiling: 'EDIT',
      path: '/Engineering',
      children: [
        {
          id: 300,
          name: 'Specs.docx',
          type: 'DOCUMENT',
          permissionCeiling: 'EDIT',
          path: '/Engineering/Specs.docx',
          children: [],
        },
      ],
    };

    service.getScopedResources(orgCode, teamId).subscribe((tree) => {
      expect(tree.id).toBe(55);
      expect(tree.children?.length).toBe(1);
      expect(tree.children?.[0].name).toBe('Specs.docx');
    });

    const req = httpMock.expectOne(`${baseUrl}/scoped-resources`);
    expect(req.request.method).toBe('GET');
    req.flush(mockTree);
  });
});
