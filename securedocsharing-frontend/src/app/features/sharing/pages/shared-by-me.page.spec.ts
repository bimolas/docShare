import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router } from '@angular/router';

import { SharedByMePageComponent } from './shared-by-me.page';
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
import { SharedItem, SharedByMeResponse } from '../models/sharing.model';

describe('SharedByMePageComponent', () => {
  let component: SharedByMePageComponent;
  let fixture: ComponentFixture<SharedByMePageComponent>;

  const mockFolders: SharedItem[] = [
    {
      permissionId: 301,
      resourceType: 'FOLDER',
      resourceId: 701,
      name: 'Q3 Budgets',
      permissionType: 'ALLOW',
      permissionLevel: 'EDIT',
      recipientType: 'GROUP',
      targetGroupId: 12,
      targetGroupName: 'Accounting Team',
      grantedAt: '2026-03-01T09:00:00Z',
      status: 'ACTIVE',
      revoked: false,
    },
  ];

  const mockDocuments: SharedItem[] = [
    {
      permissionId: 302,
      resourceType: 'DOCUMENT',
      resourceId: 801,
      name: 'Contract_Vendor_XYZ.pdf',
      fileSize: 524288,
      contentType: 'application/pdf',
      permissionType: 'ALLOW',
      permissionLevel: 'VIEW',
      recipientType: 'USER',
      targetUserName: 'David Partner',
      targetUserEmail: 'david@partner.com',
      grantedAt: '2026-03-02T11:00:00Z',
      status: 'ACTIVE',
      revoked: false,
    },
    {
      permissionId: 303,
      resourceType: 'DOCUMENT',
      resourceId: 802,
      name: 'Historical_Archive.zip',
      fileSize: 10485760,
      contentType: 'application/zip',
      permissionType: 'ALLOW',
      permissionLevel: 'DOWNLOAD',
      recipientType: 'ORGANIZATION',
      targetOrgCode: 'EXTERNAL_CORP',
      targetOrgName: 'External Partner Corp',
      grantedAt: '2026-01-01T00:00:00Z',
      status: 'REVOKED',
      revoked: true,
    },
    {
      permissionId: 304,
      resourceType: 'DOCUMENT',
      resourceId: 803,
      name: 'Joint_Venture_Plan.docx',
      fileSize: 204800,
      contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      permissionType: 'ALLOW',
      permissionLevel: 'EDIT',
      recipientType: 'COLLABORATION',
      collaborationId: 99,
      collaborationName: 'JV Project Alpha',
      grantedAt: '2026-03-05T15:00:00Z',
      status: 'ACTIVE',
      revoked: false,
    },
  ];

  const mockResponse: SharedByMeResponse = {
    sharedFolders: mockFolders,
    sharedDocuments: mockDocuments,
  };

  let mockSharingApiService: jasmine.SpyObj<SharingApiService>;
  let mockDocumentApiService: jasmine.SpyObj<DocumentApiService>;
  let mockFolderApiService: jasmine.SpyObj<FolderApiService>;
  let mockNotificationService: jasmine.SpyObj<NotificationService>;
  let mockRouter: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    mockSharingApiService = jasmine.createSpyObj('SharingApiService', [
      'getSharedByMe',
      'revokePermission',
    ]);
    mockDocumentApiService = jasmine.createSpyObj('DocumentApiService', ['getDownloadUrl']);
    mockFolderApiService = jasmine.createSpyObj('FolderApiService', ['downloadFolder']);
    mockNotificationService = jasmine.createSpyObj('NotificationService', ['info', 'success', 'error', 'warning']);
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);

    mockSharingApiService.getSharedByMe.and.returnValue(of(mockResponse));
    mockSharingApiService.revokePermission.and.returnValue(of(void 0));

    const mockAuthStore = {
      currentUser: jasmine.createSpy('currentUser').and.returnValue({
        id: 'user-me',
        email: 'me@acme.com',
        fullName: 'Current User',
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
      resourceId: 701,
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
      imports: [SharedByMePageComponent],
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

    fixture = TestBed.createComponent(SharedByMePageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component and fetch outbound shares on init', () => {
    expect(component).toBeTruthy();
    expect(mockSharingApiService.getSharedByMe).toHaveBeenCalled();
    expect(component.isLoading()).toBeFalse();
    expect(component.folderCount()).toBe(1);
    expect(component.documentCount()).toBe(3);
    expect(component.totalCount()).toBe(4);
  });

  it('should filter items by resource type', () => {
    expect(component.filteredItems().length).toBe(4);

    component.resourceTypeFilter.set('FOLDERS');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].name).toBe('Q3 Budgets');

    component.resourceTypeFilter.set('DOCUMENTS');
    expect(component.filteredItems().length).toBe(3);
  });

  it('should filter items by recipient type', () => {
    component.recipientFilter.set('USER');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].targetUserName).toBe('David Partner');

    component.recipientFilter.set('TEAM');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].targetGroupName).toBe('Accounting Team');

    component.recipientFilter.set('ORGANIZATION');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].targetOrgCode).toBe('EXTERNAL_CORP');

    component.recipientFilter.set('COLLABORATION');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].collaborationName).toBe('JV Project Alpha');
  });

  it('should filter items by status', () => {
    component.statusFilter.set('ACTIVE');
    expect(component.filteredItems().length).toBe(3);

    component.statusFilter.set('INACTIVE');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].name).toBe('Historical_Archive.zip');
  });

  it('should filter items by search query', () => {
    component.searchQuery.set('David');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].name).toBe('Contract_Vendor_XYZ.pdf');

    component.searchQuery.set('Accounting');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].name).toBe('Q3 Budgets');
  });

  it('should open item detail drawer on access tab for access management', () => {
    const item = mockFolders[0];
    component.openManageAccess(item);

    expect(component.isDrawerOpen()).toBeTrue();
    expect(component.selectedItemForDrawer()).toBeTruthy();
    expect(component.selectedItemForDrawer()?.type).toBe('folder');
    expect(component.selectedItemForDrawer()?.data.id).toBe(701);
    expect(component.selectedItemForDrawer()?.tab).toBe('access');
  });

  it('should prompt confirmation modal before revoking', () => {
    const item = mockFolders[0];
    component.confirmRevocation(item);

    expect(component.isRevokeModalOpen()).toBeTrue();
    expect(component.itemToRevoke()).toEqual(item);
    expect(mockSharingApiService.revokePermission).not.toHaveBeenCalled();

    component.cancelRevocation();
    expect(component.isRevokeModalOpen()).toBeFalse();
    expect(component.itemToRevoke()).toBeNull();
  });

  it('should execute revocation when confirmed and update item status reactively', () => {
    const item = mockFolders[0];
    component.confirmRevocation(item);

    component.executeRevocation();
    expect(mockSharingApiService.revokePermission).toHaveBeenCalledWith(301);
    expect(mockNotificationService.success).toHaveBeenCalledWith(jasmine.stringMatching(/revoked/i));
    expect(component.isRevokeModalOpen()).toBeFalse();

    const updatedFolder = component.sharedFolders().find(f => f.permissionId === 301);
    expect(updatedFolder?.revoked).toBeTrue();
    expect(updatedFolder?.status).toBe('REVOKED');
  });

  it('should handle error when revocation fails', () => {
    mockSharingApiService.revokePermission.and.returnValue(
      throwError(() => ({ error: { message: 'Revocation failed due to server error' } }))
    );

    const item = mockFolders[0];
    component.confirmRevocation(item);
    component.executeRevocation();

    expect(component.isRevoking()).toBeFalse();
    expect(mockNotificationService.error).toHaveBeenCalledWith('Revocation failed due to server error');
  });
});
