import { TestBed, ComponentFixture } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ItemDetailDrawerComponent, AccessEntryVM } from './item-detail-drawer.component';
import { PermissionDetailModalComponent } from './permission-detail-modal.component';
import { FolderApiService } from '../../../core/services/folder-api.service';
import { AuditApiService } from '../../../core/services/audit-api.service';
import { PermissionApiService } from '../../permissions/services/permission-api.service';
import { ShareApiService } from '../../permissions/services/share-api.service';
import { UserApiService } from '../../../core/services/user-api.service';
import { AuthStore } from '../../../state/auth/auth.store';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { UserProfile } from '../../../core/models/user.model';
import { EffectivePermissionsResponse } from '../../permissions/interfaces/permission.interfaces';
import { NotificationService } from '../../../core/services/notification.service';
import { AccessRequestApiService } from '../../../core/services/access-request-api.service';

describe('ItemDetailDrawerComponent — Share Action Authority Gate', () => {
  let component: ItemDetailDrawerComponent;
  let fixture: ComponentFixture<ItemDetailDrawerComponent>;

  let mockFolderApi: jasmine.SpyObj<FolderApiService>;
  let mockAuditApi: jasmine.SpyObj<AuditApiService>;
  let mockPermissionApi: jasmine.SpyObj<PermissionApiService>;
  let mockShareApi: jasmine.SpyObj<ShareApiService>;
  let mockUserApi: jasmine.SpyObj<UserApiService>;

  const currentUserSignal = signal<UserProfile | null>(null);
  const isPersonalSignal = signal<boolean>(false);

  const mockAuthStore = {
    currentUser: currentUserSignal.asReadonly(),
  };

  const mockWorkspaceContext = {
    isPersonal: () => isPersonalSignal(),
    isOrganization: () => !isPersonalSignal(),
    activeOrgCode: () => (isPersonalSignal() ? null : 'ACME'),
  };

  const mockEffectiveResponse: EffectivePermissionsResponse = {
    resourceId: 100,
    resourceType: 'FOLDER',
    resourceName: 'Test Folder',
    ownerEmail: 'creator@example.com',
    directPermissions: [],
    inheritedPermissions: [],
    activeShareLinks: [],
    userAccessSummaries: [],
  };

  beforeEach(() => {
    isPersonalSignal.set(false);
    currentUserSignal.set({
      id: 'user-1',
      email: 'user1@example.com',
      firstName: 'Test',
      lastName: 'User',
      fullName: 'Test User',
      role: 'ROLE_USER',
      createdAt: '2026-01-01T00:00:00Z',
      activeOrganizations: [
        { orgCode: 'ACME', orgName: 'Acme Corp', role: 'MEMBER', status: 'ACTIVE' },
      ],
    });

    mockFolderApi = jasmine.createSpyObj('FolderApiService', ['getFolderContents', 'getFolderPeek']);
    mockAuditApi = jasmine.createSpyObj('AuditApiService', ['getActivityLogs']);
    mockPermissionApi = jasmine.createSpyObj('PermissionApiService', [
      'getEffectivePermissions',
      'revokePermission',
      'updatePermission',
    ]);
    mockShareApi = jasmine.createSpyObj('ShareApiService', ['createShareLink', 'revokeShareLink', 'revealShareLink']);
    mockUserApi = jasmine.createSpyObj('UserApiService', ['searchUsers']);

    mockFolderApi.getFolderContents.and.returnValue(of({
      currentFolder: { id: 1, name: 'Root', createdByUserId: 'user-1', createdAt: '2026-01-01' },
      breadcrumbs: [],
      subFolders: [],
      documents: [],
    } as any));
    mockFolderApi.getFolderPeek.and.returnValue(of(null as any));
    mockAuditApi.getActivityLogs.and.returnValue(of([]));
    mockPermissionApi.getEffectivePermissions.and.returnValue(of(mockEffectiveResponse));
    mockPermissionApi.revokePermission.and.returnValue(of(undefined as any));
    mockPermissionApi.updatePermission.and.returnValue(of({} as any));
    mockShareApi.revealShareLink.and.returnValue(of({
      permissionId: 88,
      shareLinkToken: 'revealed-token-abc',
      shareUrl: 'http://localhost/share/revealed-token-abc'
    } as any));
    mockUserApi.searchUsers.and.returnValue(of([]));

    TestBed.configureTestingModule({
      imports: [ItemDetailDrawerComponent, PermissionDetailModalComponent],
      providers: [
        { provide: FolderApiService, useValue: mockFolderApi },
        { provide: AuditApiService, useValue: mockAuditApi },
        { provide: PermissionApiService, useValue: mockPermissionApi },
        { provide: ShareApiService, useValue: mockShareApi },
        { provide: UserApiService, useValue: mockUserApi },
        { provide: AuthStore, useValue: mockAuthStore },
        { provide: WorkspaceContextStore, useValue: mockWorkspaceContext },
        {
          provide: NotificationService,
          useValue: {
            success: jasmine.createSpy('success'),
            error: jasmine.createSpy('error'),
            info: jasmine.createSpy('info'),
          },
        },
        {
          provide: AccessRequestApiService,
          useValue: {
            cancelAccessRequest: jasmine.createSpy('cancelAccessRequest').and.returnValue(of(void 0)),
          },
        },
      ],
    });

    fixture = TestBed.createComponent(ItemDetailDrawerComponent);
    component = fixture.componentInstance;
  });

  // Case 1: Personal owner → Share allowed
  it('Case 1: should allow Share for personal folder and document owner', () => {
    isPersonalSignal.set(true);
    // Folder
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('item', {
      type: 'folder',
      data: { id: 10, name: 'My Folder', createdByUserId: 'user-1', subspaceType: 'PERSONAL', ownerOrgCode: null },
    });
    expect(component.canShareAccess()).toBeTrue();

    // Document
    fixture.componentRef.setInput('item', {
      type: 'document',
      data: { id: 20, fileName: 'My Doc', uploadedByUserId: 'user-1', spaceType: 'PERSONAL', ownerOrgCode: null },
    });
    expect(component.canShareAccess()).toBeTrue();
  });

  // Case 2: Personal non-owner → Share denied
  it('Case 2: should deny Share for personal resource when user is not the owner', () => {
    isPersonalSignal.set(true);
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('item', {
      type: 'folder',
      data: { id: 10, name: 'Other Folder', createdByUserId: 'user-other', subspaceType: 'PERSONAL', ownerOrgCode: null },
    });
    expect(component.canShareAccess()).toBeFalse();

    // Direct attempt to open ShareModal must do nothing
    component.openShareModal();
    expect(component.isShareModalOpen()).toBeFalse();
  });

  // Case 3: ORG_ADMIN → Share allowed
  it('Case 3: should immediately allow Share for ORG_ADMIN of resource organization', () => {
    currentUserSignal.set({
      id: 'admin-user',
      email: 'admin@acme.com',
      firstName: 'Admin',
      lastName: 'Acme',
      fullName: 'Admin Acme',
      role: 'ROLE_USER',
      createdAt: '2026-01-01T00:00:00Z',
      activeOrganizations: [
        { orgCode: 'ACME', orgName: 'Acme Corp', role: 'ROLE_ORG_ADMIN', status: 'ACTIVE' },
      ],
    });

    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('item', {
      type: 'folder',
      data: { id: 50, name: 'Org Folder', createdByUserId: 'user-someone-else', ownerOrgCode: 'ACME', spaceType: 'ORGANIZATION' },
    });

    expect(component.canShareAccess()).toBeTrue();
  });

  // Case 4: SUPER_ADMIN → Share allowed
  it('Case 4: should immediately allow Share for SUPER_ADMIN on any resource', () => {
    currentUserSignal.set({
      id: 'super-user',
      email: 'super@global.com',
      firstName: 'Super',
      lastName: 'Admin',
      fullName: 'Super Admin',
      role: 'ROLE_SUPER_ADMIN',
      createdAt: '2026-01-01T00:00:00Z',
      activeOrganizations: [],
    });

    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('item', {
      type: 'document',
      data: { id: 60, fileName: 'Project Plan', uploadedByUserId: 'user-other', ownerOrgCode: 'ACME', spaceType: 'ORGANIZATION' },
    });

    expect(component.canShareAccess()).toBeTrue();
  });

  // Case 5: Normal EDIT user who is NOT creator → Share denied
  it('Case 5: should deny Share for normal member with content EDIT when not creator', () => {
    // Normal member in org ACME, not creator of resource
    currentUserSignal.set({
      id: 'member-user',
      email: 'member@acme.com',
      firstName: 'Member',
      lastName: 'User',
      fullName: 'Member User',
      role: 'ROLE_USER',
      createdAt: '2026-01-01T00:00:00Z',
      activeOrganizations: [
        { orgCode: 'ACME', orgName: 'Acme Corp', role: 'ROLE_MEMBER', status: 'ACTIVE' },
      ],
    });

    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('item', {
      type: 'folder',
      data: { id: 70, name: 'Team Project', createdByUserId: 'creator-user', ownerOrgCode: 'ACME', spaceType: 'ORGANIZATION' },
    });

    // Backend denies authority with 403 Forbidden
    component.isPermissionAccessDenied.set(true);
    component.effectivePermissions.set(null);

    expect(component.canShareAccess()).toBeFalse();
  });

  // Case 6: TEAM_MANAGER without admin authority → Share denied
  it('Case 6: should deny Share for TEAM_MANAGER when backend returns 403', () => {
    currentUserSignal.set({
      id: 'tm-user',
      email: 'tm@acme.com',
      firstName: 'Team',
      lastName: 'Manager',
      fullName: 'Team Manager',
      role: 'ROLE_USER',
      createdAt: '2026-01-01T00:00:00Z',
      activeOrganizations: [
        { orgCode: 'ACME', orgName: 'Acme Corp', role: 'ROLE_MEMBER', status: 'ACTIVE' },
      ],
    });

    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('item', {
      type: 'document',
      data: { id: 80, fileName: 'Team Doc', uploadedByUserId: 'creator-user', ownerOrgCode: 'ACME', spaceType: 'ORGANIZATION' },
    });

    // Backend denies authority (403 Forbidden)
    component.isPermissionAccessDenied.set(true);
    component.effectivePermissions.set(null);

    expect(component.canShareAccess()).toBeFalse();
  });

  // Case 7: Creator + effective EDIT → Share allowed when backend confirms authority
  it('Case 7: should allow Share for resource creator once backend confirms authority (200 OK)', () => {
    currentUserSignal.set({
      id: 'creator-user',
      email: 'creator@acme.com',
      firstName: 'Creator',
      lastName: 'User',
      fullName: 'Creator User',
      role: 'ROLE_USER',
      createdAt: '2026-01-01T00:00:00Z',
      activeOrganizations: [
        { orgCode: 'ACME', orgName: 'Acme Corp', role: 'ROLE_MEMBER', status: 'ACTIVE' },
      ],
    });

    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('item', {
      type: 'document',
      data: { id: 90, fileName: 'My Upload', uploadedByUserId: 'creator-user', ownerOrgCode: 'ACME', spaceType: 'ORGANIZATION' },
    });

    // While loading, Share must NOT be optimistically shown
    component.isLoadingPermissions.set(true);
    expect(component.canShareAccess()).toBeFalse();

    // Backend confirms authority via HTTP 200
    component.isLoadingPermissions.set(false);
    component.isPermissionAccessDenied.set(false);
    component.effectivePermissions.set(mockEffectiveResponse);

    expect(component.canShareAccess()).toBeTrue();
  });

  // Case 8: Workspace Manager → Share allowed when backend confirms authority
  it('Case 8: should allow Share for Workspace Manager when backend returns 200 OK', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('item', {
      type: 'folder',
      data: { id: 100, name: 'Workspace Folder', createdByUserId: 'other-user', ownerOrgCode: 'ACME', spaceType: 'ORGANIZATION' },
    });

    // Backend returns 200 OK for Workspace Manager
    component.isLoadingPermissions.set(false);
    component.isPermissionAccessDenied.set(false);
    component.effectivePermissions.set(mockEffectiveResponse);

    expect(component.canShareAccess()).toBeTrue();
  });

  // Case 9: Collaboration Manager → Share allowed when backend confirms authority
  it('Case 9: should allow Share for Collaboration Manager when backend returns 200 OK', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('item', {
      type: 'document',
      data: { id: 110, fileName: 'Collab File', uploadedByUserId: 'other-user', ownerOrgCode: 'ACME', spaceType: 'ORGANIZATION' },
    });

    // Backend returns 200 OK for Collaboration Manager
    component.isLoadingPermissions.set(false);
    component.isPermissionAccessDenied.set(false);
    component.effectivePermissions.set(mockEffectiveResponse);

    expect(component.canShareAccess()).toBeTrue();
  });

  // Case 10: /effective 403 → Share remains hidden and no retry error is shown
  it('Case 10: should treat /effective 403 as authorization decision: hide Share and suppress retry banner', () => {
    mockPermissionApi.getEffectivePermissions.and.returnValue(
      throwError(() => ({ status: 403, message: 'Forbidden' }))
    );

    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('item', {
      type: 'folder',
      data: { id: 120, name: 'Restricted Folder', createdByUserId: 'other-user', ownerOrgCode: 'ACME', spaceType: 'ORGANIZATION' },
    });

    component.fetchPermissions();

    expect(component.isPermissionAccessDenied()).toBeTrue();
    expect(component.permissionError()).toBeNull();
    expect(component.canShareAccess()).toBeFalse();
  });

  // Case 11: getOwnerEmail() never falls back to current user's email
  it('Case 11: should never return currentUser email from getOwnerEmail when owner email is absent', () => {
    currentUserSignal.set({
      id: 'current-user-id',
      email: 'current-user@company.com',
      firstName: 'Current',
      lastName: 'User',
      fullName: 'Current User',
      role: 'ROLE_USER',
      createdAt: '2026-01-01T00:00:00Z',
      activeOrganizations: [],
    });

    fixture.componentRef.setInput('item', {
      type: 'folder',
      data: {
        id: 130,
        name: 'Folder with UUID creator',
        createdByUserId: 'another-user-uuid-999',
        // No email fields
      },
    });

    expect(component.getOwnerEmail()).toBe('');
    expect(component.getOwnerEmail()).not.toBe('current-user@company.com');
    expect(component.isCurrentResourceOwner()).toBeFalse();
  });

  // Case 12: Every Share entry point respects the same authority check
  it('Case 12: should prevent openShareModal from opening modal when canShareAccess is false', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('item', {
      type: 'folder',
      data: { id: 140, name: 'Locked Folder', createdByUserId: 'user-other', ownerOrgCode: 'ACME', spaceType: 'ORGANIZATION' },
    });

    // Authority denied
    component.isPermissionAccessDenied.set(true);
    component.effectivePermissions.set(null);

    expect(component.canShareAccess()).toBeFalse();
    component.openShareModal();
    expect(component.isShareModalOpen()).toBeFalse();

    // Authority granted
    component.isPermissionAccessDenied.set(false);
    component.effectivePermissions.set(mockEffectiveResponse);

    expect(component.canShareAccess()).toBeTrue();
    component.openShareModal();
    expect(component.isShareModalOpen()).toBeTrue();
  });

  describe('Cluster A — Access List Permission-ID Preservation', () => {
    // 1. Direct user permission appears with real permissionId.
    it('Case 1: direct user permission appears with real permissionId', () => {
      component.effectivePermissions.set({
        ...mockEffectiveResponse,
        directPermissions: [
          {
            id: 42,
            targetUserEmail: 'alice@example.com',
            permissionLevel: 'EDIT',
            recipientType: 'USER_PERSONAL',
          } as any,
        ],
        userAccessSummaries: [],
      });

      const list = component.getAccessList();
      const alice = list.find((e) => e.email === 'alice@example.com');
      expect(alice).toBeDefined();
      expect(alice?.permissionId).toBe(42);
      expect(alice?.id).toBe(42);
      expect(alice?.level).toBe('EDIT');
      expect(alice?.canRevoke).toBeTrue();
    });

    // 2. Summary + matching directPermission produces ONE AccessEntryVM.
    it('Case 2: summary + matching directPermission produces ONE AccessEntryVM', () => {
      component.effectivePermissions.set({
        ...mockEffectiveResponse,
        userAccessSummaries: [
          {
            email: 'alice@example.com',
            fullName: 'Alice Walker',
            effectiveAccessLevel: 'EDIT',
            accessSource: 'DIRECT',
          } as any,
        ],
        directPermissions: [
          {
            id: 42,
            targetUserEmail: 'alice@example.com',
            permissionLevel: 'EDIT',
            recipientType: 'USER_PERSONAL',
            expiresAt: '2026-12-31T23:59:59Z',
            grantedAt: '2026-01-01T10:00:00Z',
          } as any,
        ],
      });

      const list = component.getAccessList();
      const aliceEntries = list.filter((e) => e.email === 'alice@example.com');
      expect(aliceEntries.length).toBe(1);
    });

    // 3. The VM receives: permissionId, permissionLevel, expiresAt, grantedAt.
    it('Case 3: the VM receives permissionId, permissionLevel, expiresAt, grantedAt from directPermission', () => {
      component.effectivePermissions.set({
        ...mockEffectiveResponse,
        userAccessSummaries: [
          {
            email: 'alice@example.com',
            fullName: 'Alice Walker',
            effectiveAccessLevel: 'EDIT',
            accessSource: 'DIRECT',
          } as any,
        ],
        directPermissions: [
          {
            id: 42,
            targetUserEmail: 'alice@example.com',
            permissionLevel: 'EDIT',
            recipientType: 'USER_PERSONAL',
            expiresAt: '2026-12-31T23:59:59Z',
            grantedAt: '2026-01-01T10:00:00Z',
          } as any,
        ],
      });

      const list = component.getAccessList();
      const alice = list.find((e) => e.email === 'alice@example.com');
      expect(alice?.permissionId).toBe(42);
      expect(alice?.level).toBe('EDIT');
      expect(alice?.expiresAt).toBe('2026-12-31T23:59:59Z');
      expect(alice?.grantedAt).toBe('2026-01-01T10:00:00Z');
      expect(alice?.canRevoke).toBeTrue();
    });

    // 4. GROUP permission preserves permissionId.
    it('Case 4: GROUP permission preserves permissionId and sets entityType TEAM', () => {
      component.effectivePermissions.set({
        ...mockEffectiveResponse,
        directPermissions: [
          {
            id: 55,
            targetGroupId: 10,
            recipientType: 'GROUP',
            permissionLevel: 'DOWNLOAD',
            entityName: 'Backend Core Team',
          } as any,
        ],
        userAccessSummaries: [],
      });

      const list = component.getAccessList();
      const team = list.find((e) => e.permissionId === 55);
      expect(team).toBeDefined();
      expect(team?.permissionId).toBe(55);
      expect(team?.entityType).toBe('TEAM');
      expect(team?.source).toBe('TEAM');
      expect(team?.level).toBe('DOWNLOAD');
      expect(team?.canRevoke).toBeTrue();
    });

    // 5. ORGANIZATION permission preserves permissionId.
    it('Case 5: ORGANIZATION permission preserves permissionId and sets entityType ORGANIZATION', () => {
      component.effectivePermissions.set({
        ...mockEffectiveResponse,
        directPermissions: [
          {
            id: 77,
            targetOrgCode: 'ACME',
            orgName: 'Acme Global',
            recipientType: 'ORGANIZATION',
            permissionLevel: 'VIEW',
          } as any,
        ],
        userAccessSummaries: [],
      });

      const list = component.getAccessList();
      const org = list.find((e) => e.permissionId === 77);
      expect(org).toBeDefined();
      expect(org?.permissionId).toBe(77);
      expect(org?.entityType).toBe('ORGANIZATION');
      expect(org?.source).toBe('ORGANIZATION');
      expect(org?.canRevoke).toBeTrue();
    });

    // 6. A permission that exists only in summary remains view-only if no direct permission metadata exists.
    it('Case 6: permission existing only in summary remains view-only with undefined permissionId', () => {
      component.effectivePermissions.set({
        ...mockEffectiveResponse,
        userAccessSummaries: [
          {
            email: 'bob@example.com',
            fullName: 'Bob Org Member',
            effectiveAccessLevel: 'VIEW',
            accessSource: 'ORGANIZATION',
          } as any,
        ],
        directPermissions: [],
      });

      const list = component.getAccessList();
      const bob = list.find((e) => e.email === 'bob@example.com');
      expect(bob).toBeDefined();
      expect(bob?.permissionId).toBeUndefined();
      expect(bob?.canRevoke).toBeFalse();
      expect(bob?.source).toBe('ORGANIZATION');
    });

    // 7. Newly created permission appears after permissionGranted refresh with permissionId.
    it('Case 7: newly created permission appears after permissionGranted refresh with permissionId', () => {
      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 200, name: 'Folder', createdByUserId: 'user-1' },
      });

      // Initial state: no permissions
      component.effectivePermissions.set({
        ...mockEffectiveResponse,
        directPermissions: [],
        userAccessSummaries: [],
      });
      expect(component.getAccessList().some((e) => e.permissionId === 88)).toBeFalse();

      // Grant creates permission with id 88, refresh called
      mockPermissionApi.getEffectivePermissions.and.returnValue(
        of({
          ...mockEffectiveResponse,
          directPermissions: [
            {
              id: 88,
              targetUserEmail: 'newgrant@example.com',
              permissionLevel: 'EDIT',
              recipientType: 'USER_PERSONAL',
            } as any,
          ],
          userAccessSummaries: [
            {
              email: 'newgrant@example.com',
              effectiveAccessLevel: 'EDIT',
              accessSource: 'DIRECT',
            } as any,
          ],
        })
      );

      // Trigger refresh (as done by (permissionGranted)="fetchPermissions()")
      component.fetchPermissions();

      const list = component.getAccessList();
      const newPerm = list.find((e) => e.email === 'newgrant@example.com');
      expect(newPerm).toBeDefined();
      expect(newPerm?.permissionId).toBe(88);
    });

    // 8. PermissionDetailModal receives the correct permissionId.
    it('Case 8: openPermissionDetailModal receives the correct permissionId on selectedPermissionForDetail', () => {
      const entry: AccessEntryVM = {
        id: 42,
        permissionId: 42,
        name: 'Alice Walker',
        email: 'alice@example.com',
        level: 'EDIT',
        source: 'DIRECT',
        entityType: 'USER',
        canRevoke: true,
      };

      component.openPermissionDetailModal(entry);

      expect(component.isPermissionDetailOpen()).toBeTrue();
      expect(component.selectedPermissionForDetail()).toBe(entry);
      expect(component.selectedPermissionForDetail()?.permissionId).toBe(42);
    });

    // 9. Revoke uses permissionId, not userId/email.
    it('Case 9: onRevokeCardPermission calls revokePermission with real permissionId', () => {
      const entry: AccessEntryVM = {
        id: 'user-uuid-999', // userId string
        permissionId: 42, // backend Long ID
        name: 'Alice Walker',
        email: 'alice@example.com',
        level: 'EDIT',
        source: 'DIRECT',
        entityType: 'USER',
        canRevoke: true,
      };

      component.onRevokeCardPermission(entry);

      expect(mockPermissionApi.revokePermission).toHaveBeenCalledWith(42);
      expect(mockPermissionApi.revokePermission).not.toHaveBeenCalledWith('user-uuid-999');
      expect(mockPermissionApi.revokePermission).not.toHaveBeenCalledWith('alice@example.com');
    });

    // 10. Update uses permissionId, not userId/email.
    it('Case 10: PermissionDetailModalComponent saveChanges uses permissionId, not userId/email', () => {
      const modalFixture = TestBed.createComponent(PermissionDetailModalComponent);
      const modal = modalFixture.componentInstance;
      modal.permission = {
        id: 'user-uuid-999',
        permissionId: 42,
        name: 'Alice Walker',
        email: 'alice@example.com',
        level: 'VIEW',
        source: 'DIRECT',
        entityType: 'USER',
        canRevoke: true,
      };
      modal.selectedLevel.set('DOWNLOAD');

      modal.saveChanges();

      expect(mockPermissionApi.updatePermission).toHaveBeenCalledWith(
        42,
        jasmine.objectContaining({
          permissionLevel: 'DOWNLOAD' as any,
          expiresAt: undefined,
        })
      );
      expect(mockPermissionApi.updatePermission).not.toHaveBeenCalledWith(
        'user-uuid-999',
        jasmine.anything()
      );
      expect(mockPermissionApi.updatePermission).not.toHaveBeenCalledWith(
        'alice@example.com',
        jasmine.anything()
      );
    });
  });

  describe('Cluster C: Authoritative Resource Classification in ItemDetailDrawer', () => {
    it('should classify personal folder as personal space even when active workspace is organization', () => {
      isPersonalSignal.set(false); // Active UI is organization (e.g. ACME)
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 10, name: 'Personal Folder', subspaceType: 'PERSONAL', ownerOrgCode: null }
      });

      expect(component.isOrgSpace()).toBeFalse();
    });

    it('should classify personal document as personal space even when active workspace is organization', () => {
      isPersonalSignal.set(false); // Active UI is organization
      fixture.componentRef.setInput('item', {
        type: 'document',
        data: { id: 20, fileName: 'notes.pdf', spaceType: 'PERSONAL', ownerOrgCode: null }
      });

      expect(component.isOrgSpace()).toBeFalse();
    });

    it('should classify org folder as organization space even when active workspace is personal', () => {
      isPersonalSignal.set(true); // Active UI is personal
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Org Folder', subspaceType: 'ORGANIZATION_GENERAL', ownerOrgCode: 'ACME' }
      });

      expect(component.isOrgSpace()).toBeTrue();
    });

    it('should classify org document as organization space even when active workspace is personal', () => {
      isPersonalSignal.set(true); // Active UI is personal
      fixture.componentRef.setInput('item', {
        type: 'document',
        data: { id: 40, fileName: 'report.pdf', spaceType: 'ORGANIZATION', ownerOrgCode: 'ACME' }
      });

      expect(component.isOrgSpace()).toBeTrue();
    });

    it('should allow personal folder owner to share when active UI workspace is organization', () => {
      // User is logged in as user-1
      currentUserSignal.set({
        id: 'user-1',
        email: 'user1@example.com',
        firstName: 'User',
        lastName: 'One',
        fullName: 'User One',
        role: 'ROLE_USER',
        createdAt: '2026-01-01T00:00:00Z',
        activeOrganizations: [{ orgCode: 'ACME', orgName: 'Acme', role: 'ROLE_MEMBER', status: 'ACTIVE' }],
      });
      // Active workspace in UI is organization
      isPersonalSignal.set(false);

      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 77, name: 'Personal Receipts', createdByUserId: 'user-1', subspaceType: 'PERSONAL', ownerOrgCode: null },
      });

      // Must be recognized as personal and allow owner to share immediately
      expect(component.isOrgSpace()).toBeFalse();
      expect(component.canShareAccess()).toBeTrue();
    });

    it('should allow personal document owner to share when active UI workspace is organization', () => {
      currentUserSignal.set({
        id: 'user-1',
        email: 'user1@example.com',
        firstName: 'User',
        lastName: 'One',
        fullName: 'User One',
        role: 'ROLE_USER',
        createdAt: '2026-01-01T00:00:00Z',
        activeOrganizations: [{ orgCode: 'ACME', orgName: 'Acme', role: 'ROLE_MEMBER', status: 'ACTIVE' }],
      });
      isPersonalSignal.set(false);

      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('item', {
        type: 'document',
        data: { id: 88, fileName: 'personal-doc.pdf', uploadedByUserId: 'user-1', spaceType: 'PERSONAL', ownerOrgCode: null },
      });

      expect(component.isOrgSpace()).toBeFalse();
      expect(component.canShareAccess()).toBeTrue();
    });

    it('should NOT treat missing metadata as organization when active workspace is organization', () => {
      isPersonalSignal.set(false);
      fixture.componentRef.setInput('item', {
        type: 'document',
        data: { id: 99, fileName: 'unknown.txt' }
      });

      // Missing metadata does not silently become org
      expect(component.isOrgSpace()).toBeFalse();
    });
  });

  describe('Cluster D: Authoritative Personal Resource Owner Detection', () => {
    beforeEach(() => {
      currentUserSignal.set({
        id: 'user-uuid-1111',
        email: 'alice@example.com',
        firstName: 'Alice',
        lastName: 'Smith',
        fullName: 'Alice Smith',
        role: 'ROLE_USER',
        createdAt: '2026-01-01T00:00:00Z',
        activeOrganizations: [
          { orgCode: 'ACME', orgName: 'Acme Corp', role: 'ROLE_MEMBER', status: 'ACTIVE' },
        ],
      });
      isPersonalSignal.set(false); // User is browsing inside an organization context
    });

    it('1. personal folder with matching createdByUserId (case-insensitive) => isCurrentResourceOwner is true, canShareAccess is true', () => {
      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: {
          id: 101,
          name: 'My Personal Folder',
          createdByUserId: 'USER-UUID-1111', // UUID casing mismatch should be handled
          subspaceType: 'PERSONAL',
          ownerOrgCode: null,
        },
      });

      expect(component.isOrgSpace()).toBeFalse();
      expect(component.isCurrentResourceOwner()).toBeTrue();
      expect(component.canShareAccess()).toBeTrue();
      expect(component.getOwnerName()).toBe('Alice Smith');
    });

    it('2. personal document with matching uploadedByUserId => isCurrentResourceOwner is true, canShareAccess is true', () => {
      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('item', {
        type: 'document',
        data: {
          id: 202,
          fileName: 'tax_return.pdf',
          uploadedByUserId: 'user-uuid-1111',
          spaceType: 'PERSONAL',
          ownerOrgCode: null,
        },
      });

      expect(component.isOrgSpace()).toBeFalse();
      expect(component.isCurrentResourceOwner()).toBeTrue();
      expect(component.canShareAccess()).toBeTrue();
    });

    it('3. personal resource with non-matching owner user ID => isCurrentResourceOwner is false, canShareAccess is false', () => {
      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: {
          id: 102,
          name: 'Bob Personal Folder',
          createdByUserId: 'user-uuid-9999',
          subspaceType: 'PERSONAL',
          ownerOrgCode: null,
        },
      });

      expect(component.isOrgSpace()).toBeFalse();
      expect(component.isCurrentResourceOwner()).toBeFalse();
      expect(component.canShareAccess()).toBeFalse();
    });

    it('4. personal resource opened in organization UI context => ownership remains recognized without org fallback', () => {
      isPersonalSignal.set(false); // inside organization context

      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('item', {
        type: 'document',
        data: {
          id: 203,
          fileName: 'notes.txt',
          uploadedByUserId: 'user-uuid-1111',
          spaceType: 'PERSONAL',
          ownerOrgCode: null,
        },
      });

      expect(component.isOrgSpace()).toBeFalse();
      expect(component.isCurrentResourceOwner()).toBeTrue();
      expect(component.canShareAccess()).toBeTrue();
    });

    it('5. owner email matching current user when User ID is unavailable on resource data or peekData => isCurrentResourceOwner is true', () => {
      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: {
          id: 103,
          name: 'Folder Loaded Without Creator ID',
          subspaceType: 'PERSONAL',
          ownerOrgCode: null,
        },
      });

      // Backend provides authoritative effectivePermissions response with ownerEmail
      component.effectivePermissions.set({
        targetType: 'FOLDER',
        targetId: 103,
        ownerEmail: 'alice@example.com',
        userAccessSummaries: [
          { email: 'alice@example.com', effectiveAccessLevel: 'EDIT', accessSource: 'OWNER' },
        ],
        directPermissions: [],
        inheritedPermissions: [],
        shareLinks: [],
      } as any);

      expect(component.isOrgSpace()).toBeFalse();
      expect(component.isCurrentResourceOwner()).toBeTrue();
      expect(component.canShareAccess()).toBeTrue();
      expect(component.getOwnerEmail()).toBe('alice@example.com');
    });

    it('6. missing owner metadata does not incorrectly grant ownership', () => {
      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: {
          id: 104,
          name: 'Orphaned Folder',
          subspaceType: 'PERSONAL',
          ownerOrgCode: null,
        },
      });

      // No peek, no effective permissions, no creator metadata
      expect(component.isCurrentResourceOwner()).toBeFalse();
      expect(component.canShareAccess()).toBeFalse();
    });

    it('7. organization resource does not become personal-owner shareable through this logic', () => {
      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: {
          id: 501,
          name: 'Org Department Folder',
          createdByUserId: 'user-uuid-1111', // Alice created this org folder
          ownerOrgCode: 'ACME',
          spaceType: 'ORGANIZATION',
        },
      });

      expect(component.isOrgSpace()).toBeTrue();
      // Alice is the creator ID
      expect(component.isCurrentResourceOwner()).toBeTrue();

      // But because it is an ORGANIZATION resource, personal-owner bypass is NOT used:
      // Alice is only ROLE_MEMBER in ACME (not org admin), and permissions have not loaded yet:
      expect(component.canShareAccess()).toBeFalse();
    });

    it('8. full regression path: user in org context opens personal folder, owner recognized, Share available, access entry has OWNER level', () => {
      // Step A: Current user is inside an organization workspace in the UI
      isPersonalSignal.set(false);

      // Step B: User opens personal folder in Explorer
      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: {
          id: 700,
          name: 'Confidential Tax Records',
          createdByUserId: 'user-uuid-1111',
          subspaceType: 'PERSONAL',
          ownerOrgCode: null,
        },
      });

      // Step C: Resource classification evaluates to PERSONAL (no org fallback)
      expect(component.isOrgSpace()).toBeFalse();

      // Step D: Ownership evaluates to true
      expect(component.isCurrentResourceOwner()).toBeTrue();

      // Step E: Share action is available
      expect(component.canShareAccess()).toBeTrue();

      // Step F: Current user access entry reflects Owner level and OWNER source
      const accessEntry = component.getCurrentUserAccess();
      expect(accessEntry.level).toBe('Owner');
      expect(accessEntry.source).toBe('OWNER');
    });
  });

  describe('Cluster B: Share Link Management in ItemDetailDrawer', () => {
    beforeEach(() => {
      currentUserSignal.set({
        id: 'creator-user-1',
        email: 'creator@example.com',
        firstName: 'Creator',
        lastName: 'User',
        fullName: 'Creator User',
        role: 'ROLE_USER',
        createdAt: '2026-01-01T00:00:00Z',
        activeOrganizations: [
          { orgCode: 'ACME', orgName: 'Acme Corp', role: 'ROLE_MEMBER', status: 'ACTIVE' },
        ],
      });
      isPersonalSignal.set(false);
    });

    it('1. should safely handle sanitized active share link with null token without throwing', () => {
      const sanitizedLink: any = {
        id: 77,
        token: null,
        permissionLevel: 'VIEW',
        active: true,
        createdByUserId: 'creator-user-1',
        createdByUserEmail: 'creator@example.com',
        canReveal: true,
      };

      component.effectivePermissions.set({
        targetType: 'FOLDER',
        targetId: 100,
        ownerEmail: 'creator@example.com',
        userAccessSummaries: [],
        directPermissions: [],
        inheritedPermissions: [],
        activeShareLinks: [sanitizedLink],
      } as any);

      expect(() => fixture.detectChanges()).not.toThrow();
      expect(component.canRevealShareLink(sanitizedLink)).toBeTrue();
    });

    it('2. isLinkCreator should return true for matching userId and false otherwise', () => {
      const creatorLink: any = {
        id: 78,
        token: null,
        createdByUserId: 'creator-user-1',
      };
      const otherLink: any = {
        id: 79,
        token: null,
        createdByUserId: 'other-user-9',
      };

      expect(component.isLinkCreator(creatorLink)).toBeTrue();
      expect(component.isLinkCreator(otherLink)).toBeFalse();
    });

    it('3. canRevealShareLink should respect authoritative canReveal flag', () => {
      const authorizedLink: any = { id: 80, token: null, canReveal: true };
      const forbiddenAdminLink: any = { id: 81, token: null, canReveal: false };

      expect(component.canRevealShareLink(authorizedLink)).toBeTrue();
      expect(component.canRevealShareLink(forbiddenAdminLink)).toBeFalse();
    });

    it('4. copyOrRevealShareLink should call shareApiService.revealShareLink when token is null', () => {
      spyOn(component, 'copyShareLinkUrl');
      const link: any = {
        id: 88,
        token: null,
        canReveal: true,
      };

      component.copyOrRevealShareLink(link);

      expect(mockShareApi.revealShareLink).toHaveBeenCalledWith(88);
      expect(link.token).toBe('revealed-token-abc');
      expect(component.copyShareLinkUrl).toHaveBeenCalledWith('revealed-token-abc');
    });

    it('5. revokeShareLinkById should call permissionApiService.revokePermission with link id', () => {
      spyOn(window, 'confirm').and.returnValue(true);

      component.revokeShareLinkById(88);

      expect(mockPermissionApi.revokePermission).toHaveBeenCalledWith(88);
    });
  });
});


