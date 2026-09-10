import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';

import { SharedWithMePageComponent } from './shared-with-me.page';
import { SharingApiService } from '../services/sharing-api.service';
import { DocumentApiService } from '../../../core/services/document-api.service';
import { FolderApiService } from '../../../core/services/folder-api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { AuthStore } from '../../../state/auth/auth.store';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { PermissionApiService } from '../../permissions/services/permission-api.service';
import { ShareApiService } from '../../permissions/services/share-api.service';
import { UserApiService } from '../../../core/services/user-api.service';
import { AuditApiService } from '../../../core/services/audit-api.service';
import { SharedItem, SharedWithMeResponse } from '../models/sharing.model';

describe('SharedWithMePageComponent', () => {
  let component: SharedWithMePageComponent;
  let fixture: ComponentFixture<SharedWithMePageComponent>;

  const mockFolders: SharedItem[] = [
    {
      permissionId: 101,
      resourceType: 'FOLDER',
      resourceId: 501,
      name: 'Financial Reports 2026',
      grantedByName: 'Alice Admin',
      grantedByUserEmail: 'alice@acme.com',
      permissionType: 'ALLOW',
      permissionLevel: 'EDIT',
      grantedAt: '2026-03-01T10:00:00Z',
      sourceLabel: 'TEAM SHARE',
      sourceLabels: ['DIRECT SHARE', 'TEAM SHARE'],
      sourceDescription: 'Shared through Team Finance and direct user grant.',
      matchingGrantCount: 2,
      matchedPermissionIds: [101, 102],
      targetGroupName: 'Finance Team',
      ownerOrgCode: 'ACME',
      ownerOrgName: 'Acme Corp',
      workspaceId: 10,
    },
  ];

  const mockDocuments: SharedItem[] = [
    {
      permissionId: 201,
      resourceType: 'DOCUMENT',
      resourceId: 601,
      name: 'Project_Alpha_Spec.pdf',
      fileHash: 'hash-alpha-123',
      fileSize: 1048576,
      contentType: 'application/pdf',
      grantedByName: 'Bob Engineer',
      grantedByUserEmail: 'bob@partner.org',
      permissionType: 'ALLOW',
      permissionLevel: 'VIEW',
      grantedAt: '2026-03-02T12:00:00Z',
      sourceLabel: 'DIRECT SHARE',
      sourceLabels: ['DIRECT SHARE'],
      matchingGrantCount: 1,
      matchedPermissionIds: [201],
      ownerOrgCode: 'ACME',
      workspaceId: 10,
    },
    {
      permissionId: 202,
      resourceType: 'DOCUMENT',
      resourceId: 602,
      name: 'Architecture_Diagram.png',
      fileHash: 'hash-diag-456',
      fileSize: 2097152,
      contentType: 'image/png',
      grantedByName: 'Charlie Architect',
      grantedByUserEmail: 'charlie@collab.io',
      permissionType: 'ALLOW',
      permissionLevel: 'DOWNLOAD',
      grantedAt: '2026-03-03T14:00:00Z',
      sourceLabel: 'COLLABORATION SHARE',
      sourceLabels: ['COLLABORATION SHARE'],
      collaborationName: 'External Sync Collab',
      matchingGrantCount: 1,
      matchedPermissionIds: [202],
    },
  ];

  const mockResponse: SharedWithMeResponse = {
    sharedFolders: mockFolders,
    sharedDocuments: mockDocuments,
  };

  let mockSharingApiService: jasmine.SpyObj<SharingApiService>;
  let mockDocumentApiService: jasmine.SpyObj<DocumentApiService>;
  let mockFolderApiService: jasmine.SpyObj<FolderApiService>;
  let mockNotificationService: jasmine.SpyObj<NotificationService>;
  let mockRouter: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    mockSharingApiService = jasmine.createSpyObj('SharingApiService', ['getSharedWithMe']);
    mockDocumentApiService = jasmine.createSpyObj('DocumentApiService', ['getDownloadUrl']);
    mockFolderApiService = jasmine.createSpyObj('FolderApiService', ['downloadFolder']);
    mockNotificationService = jasmine.createSpyObj('NotificationService', ['info', 'success', 'error', 'warning']);
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);

    mockSharingApiService.getSharedWithMe.and.returnValue(of(mockResponse));

    const mockAuthStore = {
      currentUser: jasmine.createSpy('currentUser').and.returnValue({
        id: 'user-1',
        email: 'tester@acme.com',
        fullName: 'Test User',
        role: 'ROLE_USER',
        activeOrganizations: [{ orgCode: 'ACME', role: 'ROLE_MEMBER', status: 'ACTIVE' }],
      }),
      activeOrgCode: jasmine.createSpy('activeOrgCode').and.returnValue('ACME'),
    };

    const mockWorkspaceStore = {
      currentWorkspace: jasmine.createSpy('currentWorkspace').and.returnValue(null),
      currentWorkspaceId: jasmine.createSpy('currentWorkspaceId').and.returnValue(null),
    };

    const mockPermissionApiService = jasmine.createSpyObj('PermissionApiService', [
      'getEffectivePermissions',
      'revokePermission',
    ]);
    mockPermissionApiService.getEffectivePermissions.and.returnValue(of({
      resourceId: 501,
      resourceType: 'FOLDER',
      effectiveAccessLevel: 'EDIT',
      userAccessSummaries: [],
      directPermissions: [],
      inheritedPermissions: [],
      shareLinks: [],
    }));

    const mockShareApiService = jasmine.createSpyObj('ShareApiService', ['getShareLinks']);
    mockShareApiService.getShareLinks.and.returnValue(of([]));

    const mockUserApiService = jasmine.createSpyObj('UserApiService', ['searchUsers']);
    mockUserApiService.searchUsers.and.returnValue(of([]));

    const mockAuditApiService = jasmine.createSpyObj('AuditApiService', ['getDocumentActivityLogs']);
    mockAuditApiService.getDocumentActivityLogs.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [SharedWithMePageComponent],
      providers: [
        { provide: SharingApiService, useValue: mockSharingApiService },
        { provide: DocumentApiService, useValue: mockDocumentApiService },
        { provide: FolderApiService, useValue: mockFolderApiService },
        { provide: NotificationService, useValue: mockNotificationService },
        { provide: Router, useValue: mockRouter },
        { provide: AuthStore, useValue: mockAuthStore },
        { provide: WorkspaceContextStore, useValue: mockWorkspaceStore },
        { provide: PermissionApiService, useValue: mockPermissionApiService },
        { provide: ShareApiService, useValue: mockShareApiService },
        { provide: UserApiService, useValue: mockUserApiService },
        { provide: AuditApiService, useValue: mockAuditApiService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SharedWithMePageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component and fetch shared items on init', () => {
    expect(component).toBeTruthy();
    expect(mockSharingApiService.getSharedWithMe).toHaveBeenCalled();
    expect(component.isLoading()).toBeFalse();
    expect(component.folderCount()).toBe(1);
    expect(component.documentCount()).toBe(2);
    expect(component.totalCount()).toBe(3);
  });

  it('should compute filtered items correctly with resource type filters', () => {
    expect(component.filteredItems().length).toBe(3);

    component.resourceTypeFilter.set('FOLDERS');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].name).toBe('Financial Reports 2026');

    component.resourceTypeFilter.set('DOCUMENTS');
    expect(component.filteredItems().length).toBe(2);

    component.resourceTypeFilter.set('ALL');
    expect(component.filteredItems().length).toBe(3);
  });

  it('should filter items by search query', () => {
    component.searchQuery.set('Alpha');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].name).toBe('Project_Alpha_Spec.pdf');

    component.searchQuery.set('Alice');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].grantedByName).toBe('Alice Admin');

    component.searchQuery.set('non-matching-term');
    expect(component.filteredItems().length).toBe(0);
  });

  it('should filter items by source channel', () => {
    component.sourceFilter.set('TEAM');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].resourceId).toBe(501);

    component.sourceFilter.set('COLLABORATION');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].name).toBe('Architecture_Diagram.png');

    component.sourceFilter.set('DIRECT');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].name).toBe('Project_Alpha_Spec.pdf');
  });

  it('should filter items by permission level', () => {
    component.levelFilter.set('EDIT');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].permissionLevel).toBe('EDIT');

    component.levelFilter.set('VIEW');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].permissionLevel).toBe('VIEW');

    component.levelFilter.set('DOWNLOAD');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].permissionLevel).toBe('DOWNLOAD');
  });

  it('should open item detail drawer on access tab when openDetailsAndAccess is called', () => {
    const item = mockFolders[0];
    component.openDetailsAndAccess(item);

    expect(component.isDrawerOpen()).toBeTrue();
    expect(component.selectedItemForDrawer()).toBeTruthy();
    expect(component.selectedItemForDrawer()?.type).toBe('folder');
    expect(component.selectedItemForDrawer()?.data.id).toBe(501);
    expect(component.selectedItemForDrawer()?.tab).toBe('access');
  });

  it('should open multi-grant popover when openProvenance is called', () => {
    const item = mockFolders[0];
    component.openProvenance(item);

    expect(component.isProvenanceOpen()).toBeTrue();
    expect(component.selectedItemForProvenance()).toEqual(item);
  });

  it('should handle error state when api fails', () => {
    mockSharingApiService.getSharedWithMe.and.returnValue(
      throwError(() => ({ error: { message: 'Network connection failed' } }))
    );

    component.loadData();
    expect(component.isLoading()).toBeFalse();
    expect(component.errorMessage()).toBe('Network connection failed');
  });

  it('should toggle view mode between table and cards', () => {
    expect(component.viewMode()).toBe('table');
    component.viewMode.set('cards');
    expect(component.viewMode()).toBe('cards');
  });
});
