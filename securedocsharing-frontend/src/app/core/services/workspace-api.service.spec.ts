import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WorkspaceApiService } from './workspace-api.service';
import {
  ProjectWorkspaceResponseDto,
  CreateProjectWorkspaceRequestDto,
  UpdateProjectWorkspaceRequestDto,
  WorkspaceTeamAssignmentRequestDto,
  WorkspaceUserAssignmentRequestDto,
  WorkspaceAssignmentResponseDto,
  toProjectWorkspaceModel,
} from '../models/project-workspace.model';
import { environment } from '../../../environments/environment';

describe('WorkspaceApiService', () => {
  let service: WorkspaceApiService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiBaseUrl}/v1`;

  const mockWorkspaceDto: ProjectWorkspaceResponseDto = {
    id: 42,
    orgCode: 'ORG-ACME',
    name: 'General',
    description: 'Default organization workspace',
    rootFolderId: 101,
    rootFolderName: 'General Root',
    status: 'ACTIVE',
    createdByUserId: 'user-001',
    createdAt: '2026-09-01T10:00:00Z',
    updatedAt: '2026-09-01T10:00:00Z',
    isDefault: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        WorkspaceApiService,
      ],
    });
    service = TestBed.inject(WorkspaceApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Model Mapping', () => {
    it('should map ProjectWorkspaceResponseDto to ProjectWorkspaceModel with numeric id', () => {
      const model = toProjectWorkspaceModel(mockWorkspaceDto);

      expect(model.id).toBe(42);
      expect(typeof model.id).toBe('number');
      expect(model.organizationCode).toBe('ORG-ACME');
      expect(model.rootFolderId).toBe(101);
      expect(typeof model.rootFolderId).toBe('number');
      expect(model.rootFolderName).toBe('General Root');
      expect(model.status).toBe('ACTIVE');
      expect(model.isDefault).toBeTrue();
      // Ensure orgCode is NOT used as workspaceId
      expect(model.id as any).not.toBe('ORG-ACME');
    });

    it('should respect backend isDefault flag regardless of workspace name', () => {
      const nonDefaultGeneralDto: ProjectWorkspaceResponseDto = {
        ...mockWorkspaceDto,
        id: 43,
        name: 'General',
        rootFolderId: 102,
        isDefault: false,
      };
      const model = toProjectWorkspaceModel(nonDefaultGeneralDto);
      expect(model.id).toBe(43);
      expect(model.isDefault).toBeFalse();
    });

    it('should map personal workspace DTO with null orgCode to ProjectWorkspaceModel', () => {
      const personalDto: ProjectWorkspaceResponseDto = {
        id: 10,
        orgCode: null,
        name: 'My Personal Notes',
        description: 'Private',
        rootFolderId: 200,
        rootFolderName: 'My Personal Notes',
        status: 'ACTIVE',
        createdByUserId: 'user-001',
        createdAt: '2026-09-01T10:00:00Z',
        updatedAt: '2026-09-01T10:00:00Z',
        isDefault: true,
      };
      const model = toProjectWorkspaceModel(personalDto);
      expect(model.id).toBe(10);
      expect(model.organizationCode).toBeNull();
      expect(model.type).toBe('PERSONAL');
      expect(model.rootFolderId).toBe(200);
      expect(model.isDefault).toBeTrue();
    });
  });

  describe('API Endpoints', () => {
    it('should GET organization workspaces and map to canonical models', () => {
      const mockList: ProjectWorkspaceResponseDto[] = [
        mockWorkspaceDto,
        {
          id: 43,
          orgCode: 'ORG-ACME',
          name: 'Engineering',
          description: 'Engineering workspace',
          rootFolderId: 102,
          rootFolderName: 'Engineering Root',
          status: 'ACTIVE',
          createdByUserId: 'user-002',
          createdAt: '2026-09-02T10:00:00Z',
          updatedAt: '2026-09-02T10:00:00Z',
        },
      ];

      service.getWorkspacesForOrganization('ORG-ACME').subscribe((workspaces) => {
        expect(workspaces.length).toBe(2);
        expect(workspaces[0].id).toBe(42);
        expect(workspaces[0].organizationCode).toBe('ORG-ACME');
        expect(workspaces[0].rootFolderId).toBe(101);
        expect(workspaces[1].id).toBe(43);
        expect(workspaces[1].organizationCode).toBe('ORG-ACME');
        expect(workspaces[1].rootFolderId).toBe(102);
      });

      const req = httpMock.expectOne(`${baseUrl}/organizations/ORG-ACME/workspaces`);
      expect(req.request.method).toBe('GET');
      req.flush(mockList);
    });

    it('should POST new workspace and map response to canonical model', () => {
      const createReq: CreateProjectWorkspaceRequestDto = {
        name: 'Marketing',
        description: 'Marketing workspace',
      };

      const createdDto: ProjectWorkspaceResponseDto = {
        id: 44,
        orgCode: 'ORG-ACME',
        name: 'Marketing',
        description: 'Marketing workspace',
        rootFolderId: 103,
        rootFolderName: 'Marketing Root',
        status: 'ACTIVE',
        createdByUserId: 'user-001',
        createdAt: '2026-09-03T10:00:00Z',
        updatedAt: '2026-09-03T10:00:00Z',
      };

      service.createWorkspace('ORG-ACME', createReq).subscribe((created) => {
        expect(created.id).toBe(44);
        expect(created.organizationCode).toBe('ORG-ACME');
        expect(created.rootFolderId).toBe(103);
        expect(created.name).toBe('Marketing');
      });

      const req = httpMock.expectOne(`${baseUrl}/organizations/ORG-ACME/workspaces`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body.name).toBe('Marketing');
      expect(req.request.body.orgCode).toBe('ORG-ACME');
      req.flush(createdDto);
    });

    it('should GET workspace by numeric id', () => {
      service.getWorkspaceById(42).subscribe((ws) => {
        expect(ws.id).toBe(42);
        expect(ws.rootFolderId).toBe(101);
        expect(ws.organizationCode).toBe('ORG-ACME');
      });

      const req = httpMock.expectOne(`${baseUrl}/workspaces/42`);
      expect(req.request.method).toBe('GET');
      req.flush(mockWorkspaceDto);
    });

    it('should PATCH workspace by numeric id', () => {
      const patchReq: UpdateProjectWorkspaceRequestDto = {
        name: 'General Renamed',
        description: 'Updated description',
      };

      const patchedDto: ProjectWorkspaceResponseDto = {
        ...mockWorkspaceDto,
        name: 'General Renamed',
        description: 'Updated description',
      };

      service.updateWorkspace(42, patchReq).subscribe((ws) => {
        expect(ws.id).toBe(42);
        expect(ws.name).toBe('General Renamed');
      });

      const req = httpMock.expectOne(`${baseUrl}/workspaces/42`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual(patchReq);
      req.flush(patchedDto);
    });

    it('should POST archive workspace by numeric id', () => {
      const archivedDto: ProjectWorkspaceResponseDto = {
        ...mockWorkspaceDto,
        status: 'ARCHIVED',
      };

      service.archiveWorkspace(42).subscribe((ws) => {
        expect(ws.id).toBe(42);
        expect(ws.status).toBe('ARCHIVED');
      });

      const req = httpMock.expectOne(`${baseUrl}/workspaces/42/archive`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(archivedDto);
    });

    it('should POST restore workspace by numeric id', () => {
      const restoredDto: ProjectWorkspaceResponseDto = {
        ...mockWorkspaceDto,
        status: 'ACTIVE',
      };

      service.restoreWorkspace(42).subscribe((ws) => {
        expect(ws.id).toBe(42);
        expect(ws.status).toBe('ACTIVE');
      });

      const req = httpMock.expectOne(`${baseUrl}/workspaces/42/restore`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(restoredDto);
    });

    it('should GET workspace assignments', () => {
      const mockAssignments: WorkspaceAssignmentResponseDto[] = [
        {
          permissionId: 501,
          workspaceId: 42,
          folderId: 101,
          folderName: 'General Root',
          teamId: 10,
          teamName: 'Core Team',
          userEmail: null,
          permissionLevel: 'VIEW',
          permissionType: 'ALLOW',
          grantedByUserId: 'user-001',
          grantedByUserEmail: 'admin@acme.com',
          grantedAt: '2026-09-01T12:00:00Z',
        },
      ];

      service.getWorkspaceAssignments(42).subscribe((assignments) => {
        expect(assignments.length).toBe(1);
        expect(assignments[0].permissionId).toBe(501);
        expect(assignments[0].workspaceId).toBe(42);
        expect(assignments[0].teamId).toBe(10);
        expect(assignments[0].permissionLevel).toBe('VIEW');
      });

      const req = httpMock.expectOne(`${baseUrl}/workspaces/42/assignments`);
      expect(req.request.method).toBe('GET');
      req.flush(mockAssignments);
    });

    it('should POST assign team to workspace', () => {
      const teamReq: WorkspaceTeamAssignmentRequestDto = {
        teamId: 10,
        permissionLevel: 'EDIT',
      };

      const mockCreatedAssignment: WorkspaceAssignmentResponseDto = {
        permissionId: 502,
        workspaceId: 42,
        folderId: 101,
        folderName: 'General Root',
        teamId: 10,
        teamName: 'Core Team',
        userEmail: null,
        permissionLevel: 'EDIT',
        permissionType: 'ALLOW',
        grantedByUserId: 'user-001',
        grantedByUserEmail: 'admin@acme.com',
        grantedAt: '2026-09-01T12:00:00Z',
      };

      service.assignTeamToWorkspace(42, teamReq).subscribe((res) => {
        expect(res.permissionId).toBe(502);
        expect(res.workspaceId).toBe(42);
        expect(res.permissionLevel).toBe('EDIT');
      });

      const req = httpMock.expectOne(`${baseUrl}/workspaces/42/assignments/teams`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(teamReq);
      req.flush(mockCreatedAssignment);
    });

    it('should POST assign user to workspace', () => {
      const userReq: WorkspaceUserAssignmentRequestDto = {
        userEmail: 'dev@acme.com',
        permissionLevel: 'DOWNLOAD',
      };

      const mockCreatedAssignment: WorkspaceAssignmentResponseDto = {
        permissionId: 503,
        workspaceId: 42,
        folderId: 101,
        folderName: 'General Root',
        teamId: null,
        teamName: null,
        userEmail: 'dev@acme.com',
        permissionLevel: 'DOWNLOAD',
        permissionType: 'ALLOW',
        grantedByUserId: 'user-001',
        grantedByUserEmail: 'admin@acme.com',
        grantedAt: '2026-09-01T12:00:00Z',
      };

      service.assignUserToWorkspace(42, userReq).subscribe((res) => {
        expect(res.permissionId).toBe(503);
        expect(res.workspaceId).toBe(42);
        expect(res.userEmail).toBe('dev@acme.com');
        expect(res.permissionLevel).toBe('DOWNLOAD');
      });

      const req = httpMock.expectOne(`${baseUrl}/workspaces/42/assignments/users`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(userReq);
      req.flush(mockCreatedAssignment);
    });

    it('should DELETE workspace assignment by permissionId', () => {
      service.revokeWorkspaceAssignment(42, 501).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/workspaces/42/assignments/501`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });

    it('should GET personal workspaces and map to canonical models', () => {
      const mockPersonalList: ProjectWorkspaceResponseDto[] = [
        {
          id: 10,
          orgCode: null,
          name: 'My Personal Notes',
          description: 'Private',
          rootFolderId: 200,
          rootFolderName: 'My Personal Notes',
          status: 'ACTIVE',
          createdByUserId: 'user-001',
          createdAt: '2026-09-01T10:00:00Z',
          updatedAt: '2026-09-01T10:00:00Z',
          isDefault: true,
        },
        {
          id: 11,
          orgCode: null,
          name: 'Side Project',
          description: 'Hobby coding',
          rootFolderId: 201,
          rootFolderName: 'Side Project',
          status: 'ACTIVE',
          createdByUserId: 'user-001',
          createdAt: '2026-09-02T10:00:00Z',
          updatedAt: '2026-09-02T10:00:00Z',
          isDefault: false,
        },
      ];

      service.getPersonalWorkspaces().subscribe((workspaces) => {
        expect(workspaces.length).toBe(2);
        expect(workspaces[0].id).toBe(10);
        expect(workspaces[0].organizationCode).toBeNull();
        expect(workspaces[0].rootFolderId).toBe(200);
        expect(workspaces[0].type).toBe('PERSONAL');
        expect(workspaces[0].isDefault).toBeTrue();

        expect(workspaces[1].id).toBe(11);
        expect(workspaces[1].organizationCode).toBeNull();
        expect(workspaces[1].rootFolderId).toBe(201);
        expect(workspaces[1].type).toBe('PERSONAL');
        expect(workspaces[1].isDefault).toBeFalse();
      });

      const req = httpMock.expectOne(`${baseUrl}/personal/workspaces`);
      expect(req.request.method).toBe('GET');
      req.flush(mockPersonalList);
    });

    it('should POST create personal workspace and map to canonical model', () => {
      const createReq = {
        name: 'New Personal Project',
        description: 'Private project',
      };

      const createdDto: ProjectWorkspaceResponseDto = {
        id: 12,
        orgCode: null,
        name: 'New Personal Project',
        description: 'Private project',
        rootFolderId: 202,
        rootFolderName: 'New Personal Project',
        status: 'ACTIVE',
        createdByUserId: 'user-001',
        createdAt: '2026-09-03T10:00:00Z',
        updatedAt: '2026-09-03T10:00:00Z',
        isDefault: false,
      };

      service.createPersonalWorkspace(createReq).subscribe((created) => {
        expect(created.id).toBe(12);
        expect(created.organizationCode).toBeNull();
        expect(created.type).toBe('PERSONAL');
        expect(created.rootFolderId).toBe(202);
        expect(created.name).toBe('New Personal Project');
      });

      const req = httpMock.expectOne(`${baseUrl}/personal/workspaces`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createReq);
      req.flush(createdDto);
    });
  });
});
