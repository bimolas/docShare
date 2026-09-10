package com.subject77.docshare;

import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.organization.dto.*;
import com.subject77.docshare.modules.organization.entities.*;
import com.subject77.docshare.modules.organization.repository.*;
import com.subject77.docshare.modules.organization.services.*;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.permission.dto.PermissionUpdateRequestDto;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AuditRuntimeVerificationTest {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationInvitationService organizationInvitationService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PermissionAuditLogRepository permissionAuditLogRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private OrganizationInvitationRepository organizationInvitationRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private FolderRepository folderRepository;

    private UserEntity adminUser;
    private UserEntity targetUser;
    private OrganizationEntity testOrg;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        adminUser = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("audit_admin_" + uniqueSuffix + "@test.com")
                .firstName("AuditAdmin")
                .lastName("User")
                .role(Role.ROLE_ORG_ADMIN)
                .build());

        targetUser = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("audit_target_" + uniqueSuffix + "@test.com")
                .firstName("AuditTarget")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        testOrg = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("AUDIT_ORG_" + uniqueSuffix.toUpperCase())
                .name("Audit Test Organization " + uniqueSuffix)
                .createdBy(adminUser)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(targetUser)
                .organization(testOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("1. Organization member role change verification")
    void testOrganizationMemberRoleChange() {
        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        organizationService.updateMemberRole(testOrg.getOrgCode(), targetUser.getId(), request, adminUser);

        List<PermissionAuditLogEntity> logs = permissionAuditLogRepository.findByOrgCodeOrderByTimestampDesc(testOrg.getOrgCode());
        PermissionAuditLogEntity log = logs.stream()
                .filter(l -> "UPDATE_MEMBER_ROLE".equals(l.getAction()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("UPDATE_MEMBER_ROLE audit record not found"));

        assertEquals("ROLE_USER", log.getPreviousValue());
        assertEquals("ROLE_ORG_ADMIN", log.getNewValue());
        assertEquals(adminUser.getId(), log.getGrantedBy().getId());
        assertEquals(targetUser.getEmail(), log.getGrantedToEmail());
        assertEquals(testOrg.getOrgCode(), log.getOrgCode());
        assertNotNull(log.getTimestamp());
    }

    @Test
    @DisplayName("2. Member suspension/removal verification")
    void testMemberSuspensionAndRemoval() {
        // Suspend
        MemberStatusChangeRequestDto suspendRequest = MemberStatusChangeRequestDto.builder()
                .reason("Security audit policy violation")
                .build();

        organizationService.suspendMember(testOrg.getOrgCode(), targetUser.getId(), suspendRequest, adminUser);

        List<PermissionAuditLogEntity> logsAfterSuspend = permissionAuditLogRepository.findByOrgCodeOrderByTimestampDesc(testOrg.getOrgCode());
        PermissionAuditLogEntity suspendLog = logsAfterSuspend.stream()
                .filter(l -> "SUSPEND_MEMBER".equals(l.getAction()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("SUSPEND_MEMBER audit record not found"));

        assertEquals("ACTIVE", suspendLog.getPreviousValue());
        assertEquals("SUSPENDED", suspendLog.getNewValue());
        assertEquals("Security audit policy violation", suspendLog.getReason());
        assertEquals(adminUser.getId(), suspendLog.getGrantedBy().getId());
        assertEquals(targetUser.getEmail(), suspendLog.getGrantedToEmail());
        assertNotNull(suspendLog.getTimestamp());

        // Remove
        MemberStatusChangeRequestDto removeRequest = MemberStatusChangeRequestDto.builder()
                .reason("Permanent policy non-compliance")
                .build();

        organizationService.removeMember(testOrg.getOrgCode(), targetUser.getId(), removeRequest, adminUser);

        List<PermissionAuditLogEntity> logsAfterRemove = permissionAuditLogRepository.findByOrgCodeOrderByTimestampDesc(testOrg.getOrgCode());
        PermissionAuditLogEntity removeLog = logsAfterRemove.stream()
                .filter(l -> "REMOVE_MEMBER".equals(l.getAction()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("REMOVE_MEMBER audit record not found"));

        assertEquals("SUSPENDED", removeLog.getPreviousValue());
        assertEquals("REMOVED", removeLog.getNewValue());
        assertEquals("Permanent policy non-compliance", removeLog.getReason());
        assertEquals(adminUser.getId(), removeLog.getGrantedBy().getId());
        assertEquals(targetUser.getEmail(), removeLog.getGrantedToEmail());
        assertNotNull(removeLog.getTimestamp());
    }

    @Test
    @DisplayName("3. Permission level change verification")
    void testPermissionLevelChange() {
        PermissionEntity perm = permissionRepository.save(PermissionEntity.builder()
                .grantedBy(adminUser)
                .targetUserEmail(targetUser.getEmail())
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .documentFileHash("hash123")
                .revoked(false)
                .build());

        PermissionUpdateRequestDto updateDto = PermissionUpdateRequestDto.builder()
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        permissionService.updatePermission(perm.getId(), updateDto, adminUser.getId());

        List<PermissionAuditLogEntity> logs = permissionAuditLogRepository.findByGrantedBy_IdOrderByTimestampDesc(adminUser.getId());
        PermissionAuditLogEntity log = logs.stream()
                .filter(l -> "UPDATE_PERMISSION".equals(l.getAction()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("UPDATE_PERMISSION audit record not found"));

        assertEquals("VIEW", log.getPreviousValue());
        assertEquals("EDIT", log.getNewValue());
        assertEquals(adminUser.getId(), log.getGrantedBy().getId());
        assertEquals(targetUser.getEmail(), log.getGrantedToEmail());
        assertNotNull(log.getTimestamp());
    }

    @Test
    @DisplayName("4. Invitation expiration deduplication verification")
    void testInvitationExpirationDeduplication() {
        String inviteeEmail = "expired_test_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        // Save an invitation already expired in the past
        OrganizationInvitationEntity invitation = organizationInvitationRepository.save(OrganizationInvitationEntity.builder()
                .organization(testOrg)
                .inviter(adminUser)
                .inviteeEmail(inviteeEmail)
                .role(Role.ROLE_USER)
                .token(UUID.randomUUID().toString())
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(7, ChronoUnit.DAYS))
                .build());

        // Code path 1: Get invitation by token
        organizationInvitationService.getInvitationByToken(invitation.getToken());

        List<PermissionAuditLogEntity> logsAfterFirstCheck = permissionAuditLogRepository.findByOrgCodeOrderByTimestampDesc(testOrg.getOrgCode());
        long expiredLogsCount1 = logsAfterFirstCheck.stream()
                .filter(l -> "EXPIRED_INVITATION".equals(l.getAction()) && inviteeEmail.equals(l.getGrantedToEmail()))
                .count();

        assertEquals(1, expiredLogsCount1, "PENDING -> EXPIRED should create exactly 1 audit log record");

        // Code path 2: Re-inspect through processExpiredInvitations or accept/validate
        organizationInvitationService.processExpiredInvitations();
        try {
            organizationInvitationService.getInvitationByToken(invitation.getToken());
        } catch (Exception ignored) {}

        List<PermissionAuditLogEntity> logsAfterSecondCheck = permissionAuditLogRepository.findByOrgCodeOrderByTimestampDesc(testOrg.getOrgCode());
        long expiredLogsCount2 = logsAfterSecondCheck.stream()
                .filter(l -> "EXPIRED_INVITATION".equals(l.getAction()) && inviteeEmail.equals(l.getGrantedToEmail()))
                .count();

        assertEquals(1, expiredLogsCount2, "Re-inspecting expired invitation must not create duplicate audit records");
    }

    @Test
    @DisplayName("5. Invitation acceptance onboarding context verification")
    void testInvitationAcceptanceOnboardingContext() {
        String inviteeEmail = "accept_test_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        FolderEntity testFolder = folderRepository.save(FolderEntity.builder()
                .name("Onboarding Test Folder")
                .ownerOrg(testOrg)
                .createdBy(adminUser)
                .build());

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(inviteeEmail)
                .role(Role.ROLE_USER)
                .targetFolderId(testFolder.getId())
                .onboardingPermissionLevel(PermissionLevel.VIEW)
                .expirationDays(7)
                .build();

        OrganizationInvitationResponseDto invitationDto = organizationInvitationService.createInvitation(testOrg.getOrgCode(), request, adminUser);

        // Create user who accepts
        UserEntity newMember = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email(inviteeEmail)
                .firstName("New")
                .lastName("Member")
                .role(Role.ROLE_USER)
                .build());

        organizationInvitationService.acceptInvitation(invitationDto.getToken(), newMember);

        List<PermissionAuditLogEntity> logs = permissionAuditLogRepository.findByOrgCodeOrderByTimestampDesc(testOrg.getOrgCode());
        PermissionAuditLogEntity acceptLog = logs.stream()
                .filter(l -> "ACCEPT_INVITATION".equals(l.getAction()) && inviteeEmail.equals(l.getGrantedToEmail()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ACCEPT_INVITATION audit record not found"));

        assertNotNull(acceptLog.getDetails(), "Audit record details must not be null");
        assertTrue(acceptLog.getDetails().contains("TargetFolderId=" + testFolder.getId()) || acceptLog.getDetails().contains("OnboardingPermissionLevel=VIEW"),
                "Audit details must contain onboarding context. Actual details: " + acceptLog.getDetails());
    }
}
