import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { provideRouter } from '@angular/router';

import { MembersPageComponent } from './members.page';
import { OrganizationApiService } from '../../../core/services/organization-api.service';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { AuthStore } from '../../../state/auth/auth.store';
import { NotificationService } from '../../../core/services/notification.service';
import {
  OrganizationMemberDetailResponseDto,
  OrganizationInvitationResponseDto,
} from '../../../core/models/organization.model';

describe('MembersPageComponent', () => {
  let component: MembersPageComponent;
  let fixture: ComponentFixture<MembersPageComponent>;

  // Spies and Mock Stores
  let orgApiServiceSpy: jasmine.SpyObj<OrganizationApiService>;
  let notificationServiceSpy: jasmine.SpyObj<NotificationService>;

  let mockActiveWorkspace: ReturnType<typeof signal<any>>;
  let mockCurrentUser: ReturnType<typeof signal<any>>;

  const mockMemberList: OrganizationMemberDetailResponseDto[] = [
    {
      userId: 'user-admin-1',
      email: 'admin@acme.com',
      firstName: 'Adam',
      lastName: 'Admin',
      orgCode: 'ACME',
      role: 'ROLE_ORG_ADMIN',
      status: 'ACTIVE',
      joinedAt: '2026-01-01T00:00:00Z',
    },
    {
      userId: 'user-manager-2',
      email: 'manager@acme.com',
      firstName: 'Mandy',
      lastName: 'Manager',
      orgCode: 'ACME',
      role: 'ROLE_ORG_MANAGER',
      status: 'ACTIVE',
      joinedAt: '2026-01-15T00:00:00Z',
    },
    {
      userId: 'user-member-3',
      email: 'member@acme.com',
      firstName: 'Mike',
      lastName: 'Member',
      orgCode: 'ACME',
      role: 'ROLE_USER',
      status: 'SUSPENDED',
      statusReason: 'Security audit hold',
      joinedAt: '2026-02-01T00:00:00Z',
    },
  ];

  const mockInvitationList: OrganizationInvitationResponseDto[] = [
    {
      id: 101,
      token: 'inv-token-101',
      orgCode: 'ACME',
      inviteeEmail: 'newbie@acme.com',
      inviterEmail: 'admin@acme.com',
      role: 'ROLE_USER',
      status: 'PENDING',
      createdAt: '2026-03-01T00:00:00Z',
    },
  ];

  beforeEach(async () => {
    mockActiveWorkspace = signal({
      id: 'ws-1',
      orgCode: 'ACME',
      name: 'Acme Workspace',
    });

    mockCurrentUser = signal({
      id: 'user-admin-1',
      email: 'admin@acme.com',
      fullName: 'Adam Admin',
      role: 'ROLE_ORG_ADMIN',
      activeOrganizations: [
        {
          orgCode: 'ACME',
          role: 'ROLE_ORG_ADMIN',
          status: 'ACTIVE',
        },
      ],
    });

    orgApiServiceSpy = jasmine.createSpyObj('OrganizationApiService', [
      'getMemberDetails',
      'getOrganizationInvitations',
      'updateMemberRole',
      'suspendMember',
      'reactivateMember',
      'removeMember',
      'transferOrgAdmin',
      'revokeInvitation',
      'getSingleMemberDetail',
      'getMemberAccessSummary',
    ]);

    notificationServiceSpy = jasmine.createSpyObj('NotificationService', ['success', 'error', 'info']);

    orgApiServiceSpy.getMemberDetails.and.returnValue(of(mockMemberList));
    orgApiServiceSpy.getOrganizationInvitations.and.returnValue(of(mockInvitationList));

    await TestBed.configureTestingModule({
      imports: [MembersPageComponent],
      providers: [
        provideRouter([]),
        { provide: OrganizationApiService, useValue: orgApiServiceSpy },
        { provide: NotificationService, useValue: notificationServiceSpy },
        {
          provide: WorkspaceContextStore,
          useValue: {
            activeWorkspace: mockActiveWorkspace,
          },
        },
        {
          provide: AuthStore,
          useValue: {
            currentUser: mockCurrentUser,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MembersPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize and load members and invitations for active organization', () => {
    expect(component).toBeTruthy();
    expect(orgApiServiceSpy.getMemberDetails).toHaveBeenCalledWith('ACME', undefined, undefined);
    expect(orgApiServiceSpy.getOrganizationInvitations).toHaveBeenCalledWith('ACME');
    expect(component.members().length).toBe(3);
    expect(component.pendingInvitations().length).toBe(1);
  });

  it('should compute total, active, suspended, and activeAdmin counts accurately', () => {
    expect(component.totalMemberCount()).toBe(3);
    expect(component.activeCount()).toBe(2);
    expect(component.suspendedCount()).toBe(1);
    expect(component.activeAdminCount()).toBe(1);
  });

  it('should switch between Members and Invitations tabs', () => {
    expect(component.activeTab()).toBe('MEMBERS');

    component.setActiveTab('INVITATIONS');
    expect(component.activeTab()).toBe('INVITATIONS');

    component.setActiveTab('MEMBERS');
    expect(component.activeTab()).toBe('MEMBERS');
  });

  it('should debounce search input before calling loadMembers with query', fakeAsync(() => {
    const inputEvent = { target: { value: 'mandy' } } as unknown as Event;
    component.onSearchInput(inputEvent);

    // Initial state: not called immediately
    expect(component.searchQuery()).toBe('mandy');

    tick(300);

    expect(orgApiServiceSpy.getMemberDetails).toHaveBeenCalledWith('ACME', undefined, 'mandy');
  }));

  it('should filter by status when setStatusFilter is called', () => {
    component.setStatusFilter('SUSPENDED');
    expect(component.selectedStatus()).toBe('SUSPENDED');
    expect(orgApiServiceSpy.getMemberDetails).toHaveBeenCalledWith('ACME', 'SUSPENDED', undefined);

    component.setStatusFilter('ACTIVE');
    expect(component.selectedStatus()).toBe('ACTIVE');
    expect(orgApiServiceSpy.getMemberDetails).toHaveBeenCalledWith('ACME', 'ACTIVE', undefined);
  });

  it('should open and close the detail drawer for a member', () => {
    expect(component.isDetailDrawerOpen()).toBeFalse();

    component.openDrawerForMember(mockMemberList[0]);
    expect(component.isDetailDrawerOpen()).toBeTrue();
    expect(component.selectedMemberId()).toBe('user-admin-1');

    component.closeDetailDrawer();
    expect(component.isDetailDrawerOpen()).toBeFalse();
    expect(component.selectedMemberId()).toBeNull();
  });

  it('should handle member reactivation flow', () => {
    const suspendedMember = mockMemberList[2];
    const reactivated: OrganizationMemberDetailResponseDto = {
      ...suspendedMember,
      status: 'ACTIVE',
      statusReason: undefined,
    };

    orgApiServiceSpy.reactivateMember.and.returnValue(of(reactivated));

    component.openReactivateConfirm(suspendedMember);
    expect(component.isReactivateConfirmOpen()).toBeTrue();
    expect(component.activeModalMember()).toBe(suspendedMember);

    component.confirmReactivate();

    expect(orgApiServiceSpy.reactivateMember).toHaveBeenCalledWith('ACME', 'user-member-3');
    expect(notificationServiceSpy.success).toHaveBeenCalledWith('Mike Member has been reactivated.');
    expect(component.isReactivateConfirmOpen()).toBeFalse();

    const updated = component.members().find((m) => m.userId === 'user-member-3');
    expect(updated?.status).toBe('ACTIVE');
  });

  it('should handle revoking an invitation', () => {
    const invite = mockInvitationList[0];
    orgApiServiceSpy.revokeInvitation.and.returnValue(of(void 0));

    component.openRevokeConfirm(invite);
    expect(component.isRevokeConfirmOpen()).toBeTrue();
    expect(component.activeRevokeInvite()).toBe(invite);

    component.confirmRevokeInvitation();

    expect(orgApiServiceSpy.revokeInvitation).toHaveBeenCalledWith('ACME', 101);
    expect(notificationServiceSpy.success).toHaveBeenCalledWith(
      'Invitation for newbie@acme.com has been revoked.'
    );
    expect(component.isRevokeConfirmOpen()).toBeFalse();
    expect(component.pendingInvitations().length).toBe(0);
  });

  it('should remove member from in-memory roster on memberRemoved event', () => {
    const target = mockMemberList[1];
    component.onMemberRemoved(target);

    expect(component.members().some((m) => m.userId === target.userId)).toBeFalse();
    expect(component.allFetchedMembers().some((m) => m.userId === target.userId)).toBeFalse();
  });
});
