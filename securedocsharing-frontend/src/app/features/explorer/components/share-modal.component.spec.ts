import { TestBed, ComponentFixture } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { ShareModalComponent } from './share-modal.component';
import { UserApiService } from '../../../core/services/user-api.service';
import { PermissionApiService } from '../../permissions/services/permission-api.service';
import { ShareApiService } from '../../permissions/services/share-api.service';
import { FolderApiService } from '../../../core/services/folder-api.service';
import { AuthStore } from '../../../state/auth/auth.store';
import { OrganizationApiService } from '../../../core/services/organization-api.service';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { SelectedRecipient } from '../../permissions/interfaces/permission.interfaces';
import { UserProfile } from '../../../core/models/user.model';

describe('ShareModalComponent — Recipient Context Scoping', () => {
  let component: ShareModalComponent;
  let fixture: ComponentFixture<ShareModalComponent>;

  let mockUserApi: jasmine.SpyObj<UserApiService>;
  let mockPermissionApi: jasmine.SpyObj<PermissionApiService>;
  let mockShareApi: jasmine.SpyObj<ShareApiService>;
  let mockFolderApi: jasmine.SpyObj<FolderApiService>;
  let mockOrgApi: jasmine.SpyObj<OrganizationApiService>;

  const currentUserSignal = signal<UserProfile | null>({
    id: 'user-1',
    email: 'owner@example.com',
    firstName: 'Owner',
    lastName: 'User',
    fullName: 'Owner User',
    role: 'ROLE_USER',
    createdAt: '2026-01-01T00:00:00Z',
    activeOrganizations: [
      { orgCode: 'ACME', orgName: 'Acme Corp', role: 'MEMBER', status: 'ACTIVE' }
    ]
  });

  const isPersonalSignal = signal<boolean>(true);

  const mockAuthStore = {
    currentUser: currentUserSignal.asReadonly()
  };

  const mockWorkspaceContext = {
    isPersonal: () => isPersonalSignal(),
    isOrganization: () => !isPersonalSignal(),
    activeOrgCode: () => 'ACME'
  };

  beforeEach(() => {
    isPersonalSignal.set(true);

    mockUserApi = jasmine.createSpyObj('UserApiService', ['searchUsers']);
    mockPermissionApi = jasmine.createSpyObj('PermissionApiService', [
      'grantPermission',
      'grantFolderShareWithExclusions'
    ]);
    mockShareApi = jasmine.createSpyObj('ShareApiService', ['createShareLink']);
    mockFolderApi = jasmine.createSpyObj('FolderApiService', ['getFolderContents']);
    mockOrgApi = jasmine.createSpyObj('OrganizationApiService', [
      'searchOrganizations',
      'getUserOrganizations',
      'getOrganizationGroups'
    ]);

    mockUserApi.searchUsers.and.returnValue(of([]));
    mockOrgApi.searchOrganizations.and.returnValue(of([]));
    mockOrgApi.getUserOrganizations.and.returnValue(of([]));
    mockOrgApi.getOrganizationGroups.and.returnValue(of([]));

    TestBed.configureTestingModule({
      imports: [ShareModalComponent],
      providers: [
        { provide: UserApiService, useValue: mockUserApi },
        { provide: PermissionApiService, useValue: mockPermissionApi },
        { provide: ShareApiService, useValue: mockShareApi },
        { provide: FolderApiService, useValue: mockFolderApi },
        { provide: AuthStore, useValue: mockAuthStore },
        { provide: OrganizationApiService, useValue: mockOrgApi },
        { provide: WorkspaceContextStore, useValue: mockWorkspaceContext }
      ]
    });

    fixture = TestBed.createComponent(ShareModalComponent);
    component = fixture.componentInstance;
  });

  describe('isPersonalResource computation', () => {
    it('should detect personal resource when folder has ownerOrgCode: null', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 10, name: 'Personal Docs', ownerOrgCode: null, subspaceType: 'PERSONAL' }
      });
      expect(component.isPersonalResource()).toBeTrue();
    });

    it('should detect personal resource when document has spaceType: PERSONAL', () => {
      fixture.componentRef.setInput('item', {
        type: 'document',
        data: { id: 20, fileName: 'resume.pdf', spaceType: 'PERSONAL', ownerOrgCode: null }
      });
      expect(component.isPersonalResource()).toBeTrue();
    });

    it('should detect organization resource when folder has ownerOrgCode: "ACME"', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Eng Folder', ownerOrgCode: 'ACME', subspaceType: 'PROJECT_WORKSPACE' }
      });
      expect(component.isPersonalResource()).toBeFalse();
    });

    it('should detect organization resource when document has spaceType: ORGANIZATION', () => {
      fixture.componentRef.setInput('item', {
        type: 'document',
        data: { id: 40, fileName: 'q3-report.pdf', spaceType: 'ORGANIZATION', ownerOrgCode: 'ACME' }
      });
      expect(component.isPersonalResource()).toBeFalse();
    });

    it('should classify as PERSONAL when personal resource is in personal UI context', () => {
      isPersonalSignal.set(true);
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 10, name: 'Personal Docs', ownerOrgCode: null, subspaceType: 'PERSONAL' }
      });
      expect(component.isPersonalResource()).toBeTrue();
    });

    it('should classify as PERSONAL when personal resource is in organization UI context', () => {
      isPersonalSignal.set(false);
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 10, name: 'Personal Docs', ownerOrgCode: null, subspaceType: 'PERSONAL' }
      });
      expect(component.isPersonalResource()).toBeTrue();
    });

    it('should classify as ORGANIZATION when organization resource is in personal UI context', () => {
      isPersonalSignal.set(true);
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Org Docs', ownerOrgCode: 'ACME', subspaceType: 'ORGANIZATION_GENERAL' }
      });
      expect(component.isPersonalResource()).toBeFalse();
    });

    it('should classify as ORGANIZATION when organization resource is in organization UI context', () => {
      isPersonalSignal.set(false);
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Org Docs', ownerOrgCode: 'ACME', subspaceType: 'ORGANIZATION_GENERAL' }
      });
      expect(component.isPersonalResource()).toBeFalse();
    });

    it('should classify as PERSONAL when ownerOrgCode is explicitly null', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 10, name: 'Personal Docs', ownerOrgCode: null }
      });
      expect(component.isPersonalResource()).toBeTrue();
      expect(component.resourceOwnerOrgCode()).toBeNull();
    });

    it('should classify as ORGANIZATION when ownerOrgCode is defined and non-null', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Org Docs', ownerOrgCode: 'ACME' }
      });
      expect(component.isPersonalResource()).toBeFalse();
      expect(component.resourceOwnerOrgCode()).toBe('ACME');
    });

    it('should NOT fall back to active workspace context when metadata is missing or empty', () => {
      // Even if workspace context says ORGANIZATION (isPersonal = false, activeOrgCode = 'ACME')
      isPersonalSignal.set(false);
      fixture.componentRef.setInput('item', null);
      // Missing metadata does not silently mean organization
      expect(component.isPersonalResource()).toBeTrue();
      expect(component.resourceOwnerOrgCode()).toBeNull();

      fixture.componentRef.setInput('item', {
        type: 'document',
        data: { id: 99, fileName: 'unclassified.txt' }
      });
      expect(component.isPersonalResource()).toBeTrue();
      expect(component.resourceOwnerOrgCode()).toBeNull();
    });
  });

  describe('Recipient scoping in search results', () => {
    const candidatePersonal: SelectedRecipient = {
      selectionKey: 'USER_PERSONAL:alice@example.com',
      recipientType: 'USER_PERSONAL',
      targetUserEmail: 'alice@example.com',
      displayName: 'Alice Smith',
      displayContext: 'Personal User'
    };

    const candidateOrgMember: SelectedRecipient = {
      selectionKey: 'USER_ORGANIZATION_MEMBER:bob@example.com:ACME',
      recipientType: 'USER_ORGANIZATION_MEMBER',
      targetUserEmail: 'bob@example.com',
      targetOrgCode: 'ACME',
      displayName: 'Bob Jones',
      displayContext: 'Acme Member'
    };

    const candidateOrg: SelectedRecipient = {
      selectionKey: 'ORGANIZATION:ACME',
      recipientType: 'ORGANIZATION',
      targetOrgCode: 'ACME',
      displayName: 'Acme Corp',
      displayContext: 'Organization'
    };

    const candidateGroup: SelectedRecipient = {
      selectionKey: 'GROUP:ACME:5',
      recipientType: 'GROUP',
      targetGroupId: 5,
      targetOrgCode: 'ACME',
      displayName: 'Engineering Team',
      displayContext: 'Acme Team'
    };

    it('should suppress org members, orgs, and teams when resource is Personal', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 10, name: 'Personal Docs', ownerOrgCode: null }
      });

      component.userSearchResults.set([
        candidatePersonal,
        candidateOrgMember,
        candidateOrg,
        candidateGroup
      ]);

      expect(component.peopleResults()).toEqual([candidatePersonal]);
      expect(component.orgMemberResults()).toEqual([]);
      expect(component.organizationResults()).toEqual([]);
      expect(component.teamResults()).toEqual([]);
    });

    it('should preserve org members, orgs, and teams when resource is Organization', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Eng Folder', ownerOrgCode: 'ACME' }
      });

      component.userSearchResults.set([
        candidatePersonal,
        candidateOrgMember,
        candidateOrg,
        candidateGroup
      ]);

      expect(component.peopleResults()).toEqual([candidatePersonal]);
      expect(component.orgMemberResults()).toEqual([candidateOrgMember]);
      expect(component.organizationResults()).toEqual([candidateOrg]);
      expect(component.teamResults()).toEqual([candidateGroup]);
    });

    it('should only query users and never query orgs or groups during personal search', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 10, name: 'Personal Docs', ownerOrgCode: null }
      });

      mockUserApi.searchUsers.and.returnValue(of([
        {
          id: 'user-2',
          email: 'alice@example.com',
          firstName: 'Alice',
          lastName: 'Smith',
          roles: ['ROLE_USER'],
          activeOrganizations: [{ orgCode: 'ACME', orgName: 'Acme Corp', role: 'MEMBER' }]
        } as any
      ]));

      component.onUserSearchInput('alice');

      expect(mockUserApi.searchUsers).toHaveBeenCalledWith('alice');
      expect(mockOrgApi.searchOrganizations).not.toHaveBeenCalled();
      expect(mockOrgApi.getUserOrganizations).not.toHaveBeenCalled();
      expect(mockOrgApi.getOrganizationGroups).not.toHaveBeenCalled();

      // Only USER_PERSONAL candidate should have been created
      expect(component.userSearchResults().length).toBe(1);
      expect(component.userSearchResults()[0].recipientType).toBe('USER_PERSONAL');
      expect(component.userSearchResults()[0].targetUserEmail).toBe('alice@example.com');
    });
  });

  describe('Selection guards and pre-flight validation', () => {
    it('should prevent adding non-USER_PERSONAL recipient from search when resource is Personal', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 10, name: 'Personal Docs', ownerOrgCode: null }
      });

      const teamRecipient: SelectedRecipient = {
        selectionKey: 'GROUP:ACME:5',
        recipientType: 'GROUP',
        targetGroupId: 5,
        targetOrgCode: 'ACME',
        displayName: 'Engineering Team',
        displayContext: 'Acme Team'
      };

      component.addRecipientFromSearch(teamRecipient);
      expect(component.selectedRecipients().length).toBe(0);
    });

    it('should block granting permissions if an invalid recipient is in selected list for personal resource', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 10, name: 'Personal Docs', ownerOrgCode: null }
      });

      const invalidRecipient: SelectedRecipient = {
        selectionKey: 'GROUP:ACME:5',
        recipientType: 'GROUP',
        targetGroupId: 5,
        targetOrgCode: 'ACME',
        displayName: 'Engineering Team',
        displayContext: 'Acme Team'
      };

      // Force into selected list
      component.selectedRecipients.set([invalidRecipient]);

      component.grantCollaboratorPermission();

      expect(mockPermissionApi.grantFolderShareWithExclusions).not.toHaveBeenCalled();
      expect(component.grantErrorMsg()).toContain('Personal resources can only receive direct personal shares');
    });

    it('should grant folder share strictly with USER_PERSONAL and no org/group code for personal resource', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 10, name: 'Personal Docs', ownerOrgCode: null }
      });

      const validRecipient: SelectedRecipient = {
        selectionKey: 'USER_PERSONAL:alice@example.com',
        recipientType: 'USER_PERSONAL',
        targetUserEmail: 'alice@example.com',
        displayName: 'Alice Smith',
        displayContext: 'Personal User'
      };

      component.selectedRecipients.set([validRecipient]);
      mockPermissionApi.grantFolderShareWithExclusions.and.returnValue(of({} as any));

      component.grantCollaboratorPermission();

      expect(mockPermissionApi.grantFolderShareWithExclusions).toHaveBeenCalledWith(
        jasmine.objectContaining({
          folderId: 10,
          targetUserEmail: 'alice@example.com',
          recipientType: 'USER_PERSONAL',
          targetOrgCode: undefined,
          targetGroupId: undefined
        })
      );
    });
  });

  describe('Organization resource recipient scoping and payload construction', () => {
    it('should search groups using resource.ownerOrgCode only and not query other orgs', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Eng Folder', ownerOrgCode: 'ACME' }
      });

      mockUserApi.searchUsers.and.returnValue(of([]));
      mockOrgApi.searchOrganizations.and.returnValue(of([]));
      mockOrgApi.getOrganizationGroups.and.returnValue(of([
        { id: 101, name: 'Acme Core Team', orgCode: 'ACME' } as any
      ]));

      component.onUserSearchInput('team');

      expect(mockOrgApi.getOrganizationGroups).toHaveBeenCalledTimes(1);
      expect(mockOrgApi.getOrganizationGroups).toHaveBeenCalledWith('ACME');
      expect(mockOrgApi.getUserOrganizations).not.toHaveBeenCalled();
      expect(component.teamResults().length).toBe(1);
      expect(component.teamResults()[0].displayName).toBe('Acme Core Team');
      expect(component.teamResults()[0].targetGroupId).toBe(101);
      expect(component.teamResults()[0].targetOrgCode).toBeUndefined();
    });

    it('should only search ACME teams when caller belongs to ACME and BETA and shares ACME resource', () => {
      currentUserSignal.set({
        id: 'user-1',
        email: 'owner@example.com',
        firstName: 'Owner',
        lastName: 'User',
        fullName: 'Owner User',
        role: 'ROLE_USER',
        createdAt: '2026-01-01T00:00:00Z',
        activeOrganizations: [
          { orgCode: 'ACME', orgName: 'Acme Corp', role: 'MEMBER', status: 'ACTIVE' },
          { orgCode: 'BETA', orgName: 'Beta Inc', role: 'MEMBER', status: 'ACTIVE' }
        ]
      });

      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Eng Folder', ownerOrgCode: 'ACME' }
      });

      mockUserApi.searchUsers.and.returnValue(of([]));
      mockOrgApi.searchOrganizations.and.returnValue(of([]));
      mockOrgApi.getOrganizationGroups.and.returnValue(of([
        { id: 101, name: 'Acme Devs', orgCode: 'ACME' } as any
      ]));

      component.onUserSearchInput('devs');

      expect(mockOrgApi.getOrganizationGroups).toHaveBeenCalledWith('ACME');
      expect(mockOrgApi.getOrganizationGroups).not.toHaveBeenCalledWith('BETA');
      expect(component.teamResults().length).toBe(1);
      expect(component.teamResults()[0].displayName).toBe('Acme Devs');
    });

    it('should map target user with ACME and BETA memberships to USER_ORGANIZATION_MEMBER for ACME only', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Eng Folder', ownerOrgCode: 'ACME' }
      });

      mockUserApi.searchUsers.and.returnValue(of([
        {
          id: 'user-bob',
          email: 'bob@example.com',
          firstName: 'Bob',
          lastName: 'Jones',
          roles: ['ROLE_USER'],
          activeOrganizations: [
            { orgCode: 'ACME', orgName: 'Acme Corp', role: 'DEVELOPER', status: 'ACTIVE' },
            { orgCode: 'BETA', orgName: 'Beta Inc', role: 'ADMIN', status: 'ACTIVE' }
          ]
        } as any
      ]));

      component.onUserSearchInput('bob');

      const orgMembers = component.orgMemberResults();
      expect(orgMembers.length).toBe(1);
      expect(orgMembers[0].recipientType).toBe('USER_ORGANIZATION_MEMBER');
      expect(orgMembers[0].targetOrgCode).toBe('ACME');
      expect(orgMembers[0].targetUserEmail).toBe('bob@example.com');
      expect(orgMembers[0].orgRole).toBe('DEVELOPER');

      // Ensure no BETA USER_ORGANIZATION_MEMBER candidate exists anywhere in search results
      const betaOrgMember = component.userSearchResults().find(
        (r) => r.recipientType === 'USER_ORGANIZATION_MEMBER' && r.targetOrgCode === 'BETA'
      );
      expect(betaOrgMember).toBeUndefined();
    });

    it('should not convert external org (BETA) membership to USER_ORGANIZATION_MEMBER for ACME resource', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Eng Folder', ownerOrgCode: 'ACME' }
      });

      mockUserApi.searchUsers.and.returnValue(of([
        {
          id: 'user-charlie',
          email: 'charlie@external.com',
          firstName: 'Charlie',
          lastName: 'External',
          roles: ['ROLE_USER'],
          activeOrganizations: [
            { orgCode: 'BETA', orgName: 'Beta Inc', role: 'MEMBER', status: 'ACTIVE' }
          ]
        } as any
      ]));

      component.onUserSearchInput('charlie');

      // Charlie is not an ACME member, so orgMemberResults must be empty
      expect(component.orgMemberResults()).toEqual([]);

      // Charlie is mapped under peopleResults as USER_PERSONAL
      expect(component.peopleResults().length).toBe(1);
      expect(component.peopleResults()[0].recipientType).toBe('USER_PERSONAL');
      expect(component.peopleResults()[0].targetUserEmail).toBe('charlie@external.com');
    });

    it('should produce correct ORGANIZATION payload with targetOrgCode, no email, and no groupId', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Eng Folder', ownerOrgCode: 'ACME' }
      });

      const orgRecipient: SelectedRecipient = {
        selectionKey: 'ORGANIZATION:PARTNER_ORG',
        recipientType: 'ORGANIZATION',
        targetOrgCode: 'PARTNER_ORG',
        displayName: 'Partner Org',
        displayContext: 'Organization'
      };

      component.selectedRecipients.set([orgRecipient]);
      mockPermissionApi.grantFolderShareWithExclusions.and.returnValue(of({} as any));

      component.grantCollaboratorPermission();

      expect(mockPermissionApi.grantFolderShareWithExclusions).toHaveBeenCalledWith(
        jasmine.objectContaining({
          folderId: 30,
          recipientType: 'ORGANIZATION',
          targetOrgCode: 'PARTNER_ORG',
          targetUserEmail: undefined,
          targetGroupId: undefined
        })
      );
    });

    it('should grant folder share to internal organization member with USER_ORGANIZATION_MEMBER and targetOrgCode', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Eng Folder', ownerOrgCode: 'ACME' }
      });

      const memberRecipient: SelectedRecipient = {
        selectionKey: 'USER_ORGANIZATION_MEMBER:bob@example.com:ACME',
        recipientType: 'USER_ORGANIZATION_MEMBER',
        targetUserEmail: 'bob@example.com',
        targetOrgCode: 'ACME',
        displayName: 'Bob Jones',
        displayContext: 'Acme Member'
      };

      component.selectedRecipients.set([memberRecipient]);
      mockPermissionApi.grantFolderShareWithExclusions.and.returnValue(of({} as any));

      component.grantCollaboratorPermission();

      expect(mockPermissionApi.grantFolderShareWithExclusions).toHaveBeenCalledWith(
        jasmine.objectContaining({
          folderId: 30,
          recipientType: 'USER_ORGANIZATION_MEMBER',
          targetOrgCode: 'ACME',
          targetUserEmail: 'bob@example.com',
          targetGroupId: undefined
        })
      );
    });

    it('should grant folder share to internal team with GROUP, targetGroupId, and undefined targetOrgCode', () => {
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 30, name: 'Eng Folder', ownerOrgCode: 'ACME' }
      });

      const teamRecipient: SelectedRecipient = {
        selectionKey: 'GROUP:50',
        recipientType: 'GROUP',
        targetGroupId: 50,
        displayName: 'Dev Team',
        displayContext: 'ACME Team'
      };

      component.selectedRecipients.set([teamRecipient]);
      mockPermissionApi.grantFolderShareWithExclusions.and.returnValue(of({} as any));

      component.grantCollaboratorPermission();

      expect(mockPermissionApi.grantFolderShareWithExclusions).toHaveBeenCalledWith(
        jasmine.objectContaining({
          folderId: 30,
          recipientType: 'GROUP',
          targetGroupId: 50,
          targetOrgCode: undefined,
          targetUserEmail: undefined
        })
      );
    });

    it('should grant document share to internal team with GROUP, targetGroupId, and undefined targetOrgCode', () => {
      fixture.componentRef.setInput('item', {
        type: 'document',
        data: { id: 40, fileName: 'spec.pdf', fileHash: 'hash-123', spaceType: 'ORGANIZATION', ownerOrgCode: 'ACME' }
      });

      const teamRecipient: SelectedRecipient = {
        selectionKey: 'GROUP:50',
        recipientType: 'GROUP',
        targetGroupId: 50,
        displayName: 'Dev Team',
        displayContext: 'ACME Team'
      };

      component.selectedRecipients.set([teamRecipient]);
      mockPermissionApi.grantPermission.and.returnValue(of({} as any));

      component.grantCollaboratorPermission();

      expect(mockPermissionApi.grantPermission).toHaveBeenCalledWith(
        jasmine.objectContaining({
          documentId: 40,
          documentFileHash: 'hash-123',
          recipientType: 'GROUP',
          targetGroupId: 50,
          targetOrgCode: undefined,
          targetUserEmail: undefined
        })
      );
    });
  });

  describe('Cluster C Regression: Personal resource opened in organization UI context', () => {
    it('should classify personal folder as PERSONAL and grant with USER_PERSONAL and NO targetOrgCode', () => {
      // 1. User is currently in organization context
      isPersonalSignal.set(false);
      expect(mockWorkspaceContext.isPersonal()).toBeFalse();
      expect(mockWorkspaceContext.activeOrgCode()).toBe('ACME');

      // 2. Opens a personal folder
      fixture.componentRef.setInput('item', {
        type: 'folder',
        data: { id: 77, name: 'Personal Finances', ownerOrgCode: null, subspaceType: 'PERSONAL' }
      });

      // 3. Resource MUST still be classified as PERSONAL
      expect(component.isPersonalResource()).toBeTrue();
      expect(component.resourceOwnerOrgCode()).toBeNull();

      // 4. Personal recipient search behavior: teamResults, orgMemberResults, organizationResults MUST be empty
      mockUserApi.searchUsers.and.returnValue(of([
        {
          id: 'user-friend',
          email: 'friend@example.com',
          firstName: 'Friend',
          lastName: 'User',
          roles: ['ROLE_USER'],
          activeOrganizations: []
        } as any
      ]));
      component.onUserSearchInput('friend');
      expect(component.peopleResults().length).toBe(1);
      expect(component.orgMemberResults()).toEqual([]);
      expect(component.organizationResults()).toEqual([]);
      expect(component.teamResults()).toEqual([]);

      // 5. Sharing issues USER_PERSONAL without any targetOrgCode injected
      const recipient: SelectedRecipient = {
        selectionKey: 'USER_PERSONAL:friend@example.com',
        recipientType: 'USER_PERSONAL',
        targetUserEmail: 'friend@example.com',
        displayName: 'Friend User',
        displayContext: 'Personal User'
      };
      component.selectedRecipients.set([recipient]);
      mockPermissionApi.grantFolderShareWithExclusions.and.returnValue(of({} as any));

      component.grantCollaboratorPermission();

      expect(mockPermissionApi.grantFolderShareWithExclusions).toHaveBeenCalledWith(
        jasmine.objectContaining({
          folderId: 77,
          recipientType: 'USER_PERSONAL',
          targetUserEmail: 'friend@example.com',
          targetOrgCode: undefined,
          targetGroupId: undefined
        })
      );
    });

    it('should classify personal document as PERSONAL and grant with USER_PERSONAL and NO targetOrgCode', () => {
      // 1. User is currently in organization context
      isPersonalSignal.set(false);
      expect(mockWorkspaceContext.isPersonal()).toBeFalse();
      expect(mockWorkspaceContext.activeOrgCode()).toBe('ACME');

      // 2. Opens a personal document
      fixture.componentRef.setInput('item', {
        type: 'document',
        data: { id: 88, fileName: 'taxes.pdf', fileHash: 'hash-tax', spaceType: 'PERSONAL', ownerOrgCode: null }
      });

      // 3. Resource MUST still be classified as PERSONAL
      expect(component.isPersonalResource()).toBeTrue();
      expect(component.resourceOwnerOrgCode()).toBeNull();

      // 4. Personal recipient sharing: issues USER_PERSONAL without targetOrgCode
      const recipient: SelectedRecipient = {
        selectionKey: 'USER_PERSONAL:friend@example.com',
        recipientType: 'USER_PERSONAL',
        targetUserEmail: 'friend@example.com',
        displayName: 'Friend User',
        displayContext: 'Personal User'
      };
      component.selectedRecipients.set([recipient]);
      mockPermissionApi.grantPermission.and.returnValue(of({} as any));

      component.grantCollaboratorPermission();

      expect(mockPermissionApi.grantPermission).toHaveBeenCalledWith(
        jasmine.objectContaining({
          documentId: 88,
          documentFileHash: 'hash-tax',
          recipientType: 'USER_PERSONAL',
          targetUserEmail: 'friend@example.com',
          targetOrgCode: undefined,
          targetGroupId: undefined
        })
      );
    });
  });
});
