import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { of } from 'rxjs';

import { orgMembersGuard } from '../../core/guards/auth.guard';
import { WorkspaceContextStore } from '../../state/workspace/workspace-context.store';
import { AuthStore } from '../../state/auth/auth.store';
import { OrganizationApiService } from '../../core/services/organization-api.service';
import { NotificationService } from '../../core/services/notification.service';
import { OrganizationMemberDetailResponseDto } from '../../core/models/organization.model';

import { MemberTableComponent } from './components/member-table/member-table.component';
import { InviteMemberModalComponent } from './components/invite-member-modal/invite-member-modal.component';
import { ChangeRoleModalComponent } from './components/modals/change-role-modal.component';

describe('Members Section Critical Authorization Matrix', () => {
  describe('orgMembersGuard Route Authorization', () => {
    let mockCurrentUser: ReturnType<typeof signal<any>>;
    let mockIsOrganization: ReturnType<typeof signal<boolean>>;
    let mockActiveWorkspace: ReturnType<typeof signal<any>>;
    let mockActiveOrganization: ReturnType<typeof signal<any>>;
    let router: Router;

    beforeEach(() => {
      mockCurrentUser = signal(null);
      mockIsOrganization = signal(true);
      mockActiveWorkspace = signal({ id: 'ws-100', orgCode: 'ACME' });
      mockActiveOrganization = signal({ orgCode: 'ACME', name: 'Acme Corp' });

      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          {
            provide: WorkspaceContextStore,
            useValue: {
              isOrganization: mockIsOrganization,
              activeWorkspace: mockActiveWorkspace,
              activeOrganization: mockActiveOrganization,
            },
          },
          {
            provide: AuthStore,
            useValue: {
              currentUser: mockCurrentUser,
            },
          },
        ],
      });

      router = TestBed.inject(Router);
    });

    it('should block standard ROLE_USER from accessing Members route and redirect to workspace explorer', () => {
      mockCurrentUser.set({
        id: 'user-standard',
        email: 'user@acme.com',
        role: 'ROLE_USER',
        activeOrganizations: [
          {
            orgCode: 'ACME',
            role: 'ROLE_USER',
            status: 'ACTIVE',
          },
        ],
      });

      const result = TestBed.runInInjectionContext(() =>
        orgMembersGuard({} as any, {} as any)
      );

      expect(result instanceof UrlTree).toBeTrue();
      expect((result as UrlTree).toString()).toBe('/w/ws-100/explorer');
    });

    it('should allow ROLE_ORG_ADMIN possessing ORG_MEMBERS_MANAGE to access Members route', () => {
      mockCurrentUser.set({
        id: 'admin-user',
        email: 'admin@acme.com',
        role: 'ROLE_ORG_ADMIN',
        activeOrganizations: [
          {
            orgCode: 'ACME',
            role: 'ROLE_ORG_ADMIN',
            status: 'ACTIVE',
          },
        ],
      });

      const result = TestBed.runInInjectionContext(() =>
        orgMembersGuard({} as any, {} as any)
      );

      expect(result).toBeTrue();
    });

    it('should allow ROLE_ORG_MANAGER possessing ORG_MEMBERS_MANAGE to access Members route', () => {
      mockCurrentUser.set({
        id: 'manager-user',
        email: 'manager@acme.com',
        role: 'ROLE_ORG_MANAGER',
        activeOrganizations: [
          {
            orgCode: 'ACME',
            role: 'ROLE_ORG_MANAGER',
            status: 'ACTIVE',
          },
        ],
      });

      const result = TestBed.runInInjectionContext(() =>
        orgMembersGuard({} as any, {} as any)
      );

      expect(result).toBeTrue();
    });

    it('should redirect to personal dashboard when not in an organization workspace', () => {
      mockIsOrganization.set(false);

      const result = TestBed.runInInjectionContext(() =>
        orgMembersGuard({} as any, {} as any)
      );

      expect(result instanceof UrlTree).toBeTrue();
      expect((result as UrlTree).toString()).toBe('/w/personal/dashboard');
    });
  });

  describe('MemberTableComponent Action Governance Rules', () => {
    let fixture: any;
    let table: MemberTableComponent;

    const adminMember: OrganizationMemberDetailResponseDto = {
      userId: 'target-admin',
      email: 'admin@acme.com',
      firstName: 'Adam',
      lastName: 'Admin',
      orgCode: 'ACME',
      joinedAt: '2026-01-01T00:00:00Z',
      role: 'ROLE_ORG_ADMIN',
      status: 'ACTIVE',
    };

    const regularMember: OrganizationMemberDetailResponseDto = {
      userId: 'target-user',
      email: 'user@acme.com',
      firstName: 'Regular',
      lastName: 'User',
      orgCode: 'ACME',
      joinedAt: '2026-02-01T00:00:00Z',
      role: 'ROLE_USER',
      status: 'ACTIVE',
    };

    beforeEach(() => {
      TestBed.configureTestingModule({
        imports: [MemberTableComponent],
      });
      fixture = TestBed.createComponent(MemberTableComponent);
      table = fixture.componentInstance;
    });

    it('should prevent ORG_MANAGER from modifying, suspending, removing, or transferring admin to an ORG_ADMIN', () => {
      fixture.componentRef.setInput('currentUserRole', 'ROLE_ORG_MANAGER');
      fixture.componentRef.setInput('currentUserId', 'manager-123');
      fixture.componentRef.setInput('activeAdminCount', 2);
      fixture.detectChanges();

      // Manager targeting an Admin
      expect(table.canChangeRole(adminMember)).toBeFalse();
      expect(table.canSuspend(adminMember)).toBeFalse();
      expect(table.canRemove(adminMember)).toBeFalse();
      expect(table.canTransferAdmin(adminMember)).toBeFalse();

      // Manager targeting a regular member should be allowed
      expect(table.canChangeRole(regularMember)).toBeTrue();
      expect(table.canSuspend(regularMember)).toBeTrue();
      expect(table.canRemove(regularMember)).toBeTrue();
    });

    it('should prevent self-actions (cannot self-change role, self-suspend, self-remove, or self-transfer)', () => {
      const selfAdmin: OrganizationMemberDetailResponseDto = {
        ...adminMember,
        userId: 'self-admin-id',
      };

      fixture.componentRef.setInput('currentUserRole', 'ROLE_ORG_ADMIN');
      fixture.componentRef.setInput('currentUserId', 'self-admin-id');
      fixture.componentRef.setInput('activeAdminCount', 2);
      fixture.detectChanges();

      expect(table.isSelf(selfAdmin)).toBeTrue();
      expect(table.canChangeRole(selfAdmin)).toBeFalse();
      expect(table.canSuspend(selfAdmin)).toBeFalse();
      expect(table.canRemove(selfAdmin)).toBeFalse();
      expect(table.canTransferAdmin(selfAdmin)).toBeFalse();
    });

    it('should protect sole active admin from demotion, suspension, and removal', () => {
      fixture.componentRef.setInput('currentUserRole', 'ROLE_ORG_ADMIN');
      fixture.componentRef.setInput('currentUserId', 'another-user');
      fixture.componentRef.setInput('activeAdminCount', 1); // Only 1 active admin exists
      fixture.detectChanges();

      expect(table.isSoleAdmin(adminMember)).toBeTrue();
      expect(table.canChangeRole(adminMember)).toBeFalse();
      expect(table.canSuspend(adminMember)).toBeFalse();
      expect(table.canRemove(adminMember)).toBeFalse();
    });
  });

  describe('Modal Role Boundaries', () => {
    let orgApiSpy: jasmine.SpyObj<OrganizationApiService>;
    let notifSpy: jasmine.SpyObj<NotificationService>;

    beforeEach(() => {
      orgApiSpy = jasmine.createSpyObj('OrganizationApiService', [
        'createInvitation',
        'updateMemberRole',
      ]);
      notifSpy = jasmine.createSpyObj('NotificationService', ['success', 'error']);

      TestBed.configureTestingModule({
        imports: [InviteMemberModalComponent, ChangeRoleModalComponent],
        providers: [
          { provide: OrganizationApiService, useValue: orgApiSpy },
          { provide: NotificationService, useValue: notifSpy },
        ],
      });
    });

    it('should prevent ORG_MANAGER from selecting/inviting ORG_ADMIN in InviteMemberModalComponent', () => {
      const inviteFixture = TestBed.createComponent(InviteMemberModalComponent);
      inviteFixture.componentRef.setInput('currentUserRole', 'ROLE_ORG_MANAGER');
      inviteFixture.detectChanges();

      const options = inviteFixture.componentInstance.availableRoleOptions();
      const hasAdmin = options.some((o) => o.value === 'ROLE_ORG_ADMIN');
      const hasManager = options.some((o) => o.value === 'ROLE_ORG_MANAGER');
      const hasUser = options.some((o) => o.value === 'ROLE_USER');

      expect(hasAdmin).toBeFalse();
      expect(hasManager).toBeTrue();
      expect(hasUser).toBeTrue();
    });

    it('should allow ORG_ADMIN to invite with ROLE_ORG_ADMIN in InviteMemberModalComponent', () => {
      const inviteFixture = TestBed.createComponent(InviteMemberModalComponent);
      inviteFixture.componentRef.setInput('currentUserRole', 'ROLE_ORG_ADMIN');
      inviteFixture.detectChanges();

      const options = inviteFixture.componentInstance.availableRoleOptions();
      const hasAdmin = options.some((o) => o.value === 'ROLE_ORG_ADMIN');

      expect(hasAdmin).toBeTrue();
    });

    it('should block assigning managerial roles to suspended or non-active members in ChangeRoleModalComponent', () => {
      const roleFixture = TestBed.createComponent(ChangeRoleModalComponent);
      const suspendedMember: OrganizationMemberDetailResponseDto = {
        userId: 'suspended-user',
        email: 'suspended@acme.com',
        orgCode: 'ACME',
        joinedAt: '2026-02-15T00:00:00Z',
        role: 'ROLE_USER',
        status: 'SUSPENDED',
      };

      roleFixture.componentRef.setInput('member', suspendedMember);
      roleFixture.componentRef.setInput('currentUserRole', 'ROLE_ORG_ADMIN');
      roleFixture.componentRef.setInput('open', true);
      roleFixture.detectChanges();

      // Setting role to ORG_MANAGER while member is SUSPENDED
      roleFixture.componentInstance.selectedRole.set('ROLE_ORG_MANAGER');
      roleFixture.detectChanges();

      expect(roleFixture.componentInstance.isNonActiveMemberRoleBlocked()).toBeTrue();
      expect(roleFixture.componentInstance.isActionBlocked()).toBeTrue();
    });
  });
});
