package com.subject77.docshare.modules.trust.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.services.CapabilityEvaluatorService;
import com.subject77.docshare.modules.trust.dto.CreateTrustRequestDto;
import com.subject77.docshare.modules.trust.dto.TrustRelationshipResponseDto;
import com.subject77.docshare.modules.trust.entities.OrgTrustRelationshipEntity;
import com.subject77.docshare.modules.trust.entities.TrustStatus;
import com.subject77.docshare.modules.trust.repository.OrgTrustRelationshipRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrgTrustRelationshipServiceImplTest {

    @Mock
    private OrgTrustRelationshipRepository trustRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private CapabilityEvaluatorService capabilityEvaluatorService;

    @InjectMocks
    private OrgTrustRelationshipServiceImpl trustService;

    private OrganizationEntity orgA;
    private OrganizationEntity orgB;
    private UserEntity adminA;
    private UserEntity memberA;
    private UserEntity adminB;

    @BeforeEach
    void setUp() {
        orgA = OrganizationEntity.builder()
                .orgCode("ORG_A")
                .name("Organization A")
                .build();

        orgB = OrganizationEntity.builder()
                .orgCode("ORG_B")
                .name("Organization B")
                .build();

        adminA = UserEntity.builder()
                .id("user-admin-a")
                .email("admin@orga.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        memberA = UserEntity.builder()
                .id("user-member-a")
                .email("member@orga.com")
                .role(Role.ROLE_USER)
                .build();

        adminB = UserEntity.builder()
                .id("user-admin-b")
                .email("admin@orgb.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build();
    }

    @Test
    @DisplayName("1. createTrustRequest - Success: Org admin creates trust request in PENDING state")
    void createTrustRequest_Success() {
        CreateTrustRequestDto dto = CreateTrustRequestDto.builder()
                .sourceOrgCode("ORG_A")
                .targetOrgCode("ORG_B")
                .build();

        when(organizationRepository.findByOrgCodeForUpdate("ORG_A")).thenReturn(Optional.of(orgA));
        when(organizationRepository.findByOrgCodeForUpdate("ORG_B")).thenReturn(Optional.of(orgB));

        UserOrganizationEntity adminMembership = UserOrganizationEntity.builder()
                .user(adminA)
                .organization(orgA)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-admin-a", "ORG_A"))
                .thenReturn(Optional.of(adminMembership));

        when(trustRepository.findByOrganizationPairAndStatusIn(eq("ORG_A"), eq("ORG_B"), anyList()))
                .thenReturn(Collections.emptyList());

        OrgTrustRelationshipEntity savedEntity = OrgTrustRelationshipEntity.builder()
                .id(100L)
                .sourceOrganization(orgA)
                .targetOrganization(orgB)
                .status(TrustStatus.PENDING)
                .requestedBy(adminA)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(trustRepository.save(any(OrgTrustRelationshipEntity.class))).thenReturn(savedEntity);

        TrustRelationshipResponseDto response = trustService.createTrustRequest(dto, adminA);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("ORG_A", response.getSourceOrgCode());
        assertEquals("ORG_B", response.getTargetOrgCode());
        assertEquals(TrustStatus.PENDING, response.getStatus());
        assertEquals("user-admin-a", response.getRequestedByUserId());
        assertFalse(response.isActive());

        verify(trustRepository).save(any(OrgTrustRelationshipEntity.class));
        verify(auditLogService).logPermissionChange(
                eq("TRUST_REQUESTED"), eq("ORGANIZATION"), isNull(),
                isNull(), isNull(), isNull(), eq("ORG_A"), eq(adminA),
                isNull(), eq("ORG_B"), any(), isNull(), isNull()
        );
    }

    @Test
    @DisplayName("2. createTrustRequest - Self Trust Failure: Throws IllegalArgumentException")
    void createTrustRequest_SelfTrust_Failure() {
        CreateTrustRequestDto dto = CreateTrustRequestDto.builder()
                .sourceOrgCode("ORG_A")
                .targetOrgCode("ORG_A")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                trustService.createTrustRequest(dto, adminA));

        assertEquals("An organization cannot establish a trust relationship with itself", ex.getMessage());
        verifyNoInteractions(trustRepository);
    }

    @Test
    @DisplayName("3. createTrustRequest - Duplicate Active/Pending Relationship: Throws IllegalStateException")
    void createTrustRequest_DuplicateActiveOrPending_Failure() {
        CreateTrustRequestDto dto = CreateTrustRequestDto.builder()
                .sourceOrgCode("ORG_A")
                .targetOrgCode("ORG_B")
                .build();

        when(organizationRepository.findByOrgCodeForUpdate("ORG_A")).thenReturn(Optional.of(orgA));
        when(organizationRepository.findByOrgCodeForUpdate("ORG_B")).thenReturn(Optional.of(orgB));

        UserOrganizationEntity adminMembership = UserOrganizationEntity.builder()
                .user(adminA)
                .organization(orgA)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-admin-a", "ORG_A"))
                .thenReturn(Optional.of(adminMembership));

        OrgTrustRelationshipEntity existingTrust = OrgTrustRelationshipEntity.builder()
                .id(101L)
                .sourceOrganization(orgA)
                .targetOrganization(orgB)
                .status(TrustStatus.PENDING)
                .build();

        when(trustRepository.findByOrganizationPairAndStatusIn(eq("ORG_A"), eq("ORG_B"), anyList()))
                .thenReturn(List.of(existingTrust));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                trustService.createTrustRequest(dto, adminA));

        assertEquals("An active or pending trust relationship already exists between these organizations", ex.getMessage());
        verify(trustRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. createTrustRequest - Non-Admin User: Throws AccessDeniedException")
    void createTrustRequest_NonAdminUser_Failure() {
        CreateTrustRequestDto dto = CreateTrustRequestDto.builder()
                .sourceOrgCode("ORG_A")
                .targetOrgCode("ORG_B")
                .build();

        when(organizationRepository.findByOrgCodeForUpdate("ORG_A")).thenReturn(Optional.of(orgA));
        when(organizationRepository.findByOrgCodeForUpdate("ORG_B")).thenReturn(Optional.of(orgB));

        UserOrganizationEntity regularMembership = UserOrganizationEntity.builder()
                .user(memberA)
                .organization(orgA)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-member-a", "ORG_A"))
                .thenReturn(Optional.of(regularMembership));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                trustService.createTrustRequest(dto, memberA));

        assertEquals("Admin privileges required in organization: ORG_A", ex.getMessage());
        verify(trustRepository, never()).save(any());
    }

    @Test
    @DisplayName("4b. createTrustRequest - Suspended Admin User: Throws AccessDeniedException")
    void createTrustRequest_SuspendedAdmin_Failure() {
        CreateTrustRequestDto dto = CreateTrustRequestDto.builder()
                .sourceOrgCode("ORG_A")
                .targetOrgCode("ORG_B")
                .build();

        when(organizationRepository.findByOrgCodeForUpdate("ORG_A")).thenReturn(Optional.of(orgA));
        when(organizationRepository.findByOrgCodeForUpdate("ORG_B")).thenReturn(Optional.of(orgB));

        UserOrganizationEntity suspendedAdminMembership = UserOrganizationEntity.builder()
                .user(adminA)
                .organization(orgA)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.SUSPENDED)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-admin-a", "ORG_A"))
                .thenReturn(Optional.of(suspendedAdminMembership));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                trustService.createTrustRequest(dto, adminA));

        assertEquals("User organization membership is not active in organization: ORG_A", ex.getMessage());
        verify(trustRepository, never()).save(any());
    }

    @Test
    @DisplayName("5. approveTrustRequest - Success: Target org admin approves request to ACTIVE state")
    void approveTrustRequest_Success() {
        OrgTrustRelationshipEntity pendingTrust = OrgTrustRelationshipEntity.builder()
                .id(100L)
                .sourceOrganization(orgA)
                .targetOrganization(orgB)
                .status(TrustStatus.PENDING)
                .requestedBy(adminA)
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .updatedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(trustRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(pendingTrust));

        UserOrganizationEntity adminBMembership = UserOrganizationEntity.builder()
                .user(adminB)
                .organization(orgB)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-admin-b", "ORG_B"))
                .thenReturn(Optional.of(adminBMembership));

        when(trustRepository.save(any(OrgTrustRelationshipEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrustRelationshipResponseDto response = trustService.approveTrustRequest(100L, adminB);

        assertNotNull(response);
        assertEquals(TrustStatus.ACTIVE, response.getStatus());
        assertEquals("user-admin-b", response.getApprovedByUserId());
        assertTrue(response.isActive());

        verify(trustRepository).save(pendingTrust);
        verify(auditLogService).logPermissionChange(
                eq("TRUST_APPROVED"), eq("ORGANIZATION"), isNull(),
                isNull(), isNull(), isNull(), eq("ORG_B"), eq(adminB),
                isNull(), eq("ORG_A"), any(), isNull(), isNull()
        );
    }

    @Test
    @DisplayName("5b. approveTrustRequest - Suspended Target Admin: Throws AccessDeniedException")
    void approveTrustRequest_SuspendedAdmin_Failure() {
        OrgTrustRelationshipEntity pendingTrust = OrgTrustRelationshipEntity.builder()
                .id(100L)
                .sourceOrganization(orgA)
                .targetOrganization(orgB)
                .status(TrustStatus.PENDING)
                .requestedBy(adminA)
                .build();

        when(trustRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(pendingTrust));

        UserOrganizationEntity suspendedMembership = UserOrganizationEntity.builder()
                .user(adminB)
                .organization(orgB)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.SUSPENDED)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-admin-b", "ORG_B"))
                .thenReturn(Optional.of(suspendedMembership));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                trustService.approveTrustRequest(100L, adminB));

        assertEquals("User organization membership is not active in organization: ORG_B", ex.getMessage());
        verify(trustRepository, never()).save(any());
    }

    @Test
    @DisplayName("6. approveTrustRequest - Non-Target Admin: Throws AccessDeniedException when source admin attempts approval")
    void approveTrustRequest_NonTargetAdmin_Failure() {
        OrgTrustRelationshipEntity pendingTrust = OrgTrustRelationshipEntity.builder()
                .id(100L)
                .sourceOrganization(orgA)
                .targetOrganization(orgB)
                .status(TrustStatus.PENDING)
                .requestedBy(adminA)
                .build();

        when(trustRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(pendingTrust));

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-admin-a", "ORG_B"))
                .thenReturn(Optional.empty());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                trustService.approveTrustRequest(100L, adminA));

        assertEquals("User is not a member of organization: ORG_B", ex.getMessage());
        verify(trustRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. approveTrustRequest - Expired Request: Throws IllegalStateException")
    void approveTrustRequest_ExpiredRequest_Failure() {
        OrgTrustRelationshipEntity expiredTrust = OrgTrustRelationshipEntity.builder()
                .id(100L)
                .sourceOrganization(orgA)
                .targetOrganization(orgB)
                .status(TrustStatus.PENDING)
                .requestedBy(adminA)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(trustRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(expiredTrust));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                trustService.approveTrustRequest(100L, adminB));

        assertEquals("Trust request has expired", ex.getMessage());
        verify(trustRepository, never()).save(any());
    }

    @Test
    @DisplayName("8. approveTrustRequest - Invalid State: Throws IllegalStateException when not PENDING")
    void approveTrustRequest_InvalidState_Failure() {
        OrgTrustRelationshipEntity activeTrust = OrgTrustRelationshipEntity.builder()
                .id(100L)
                .sourceOrganization(orgA)
                .targetOrganization(orgB)
                .status(TrustStatus.ACTIVE)
                .requestedBy(adminA)
                .approvedBy(adminB)
                .build();

        when(trustRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activeTrust));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                trustService.approveTrustRequest(100L, adminB));

        assertEquals("Trust request is not in PENDING state", ex.getMessage());
        verify(trustRepository, never()).save(any());
    }

    @Test
    @DisplayName("9. revokeTrustRelationship - Success: Source org admin revokes relationship")
    void revokeTrustRelationship_Success() {
        OrgTrustRelationshipEntity activeTrust = OrgTrustRelationshipEntity.builder()
                .id(100L)
                .sourceOrganization(orgA)
                .targetOrganization(orgB)
                .status(TrustStatus.ACTIVE)
                .requestedBy(adminA)
                .approvedBy(adminB)
                .build();

        when(trustRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activeTrust));

        UserOrganizationEntity adminAMembership = UserOrganizationEntity.builder()
                .user(adminA)
                .organization(orgA)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-admin-a", "ORG_A"))
                .thenReturn(Optional.of(adminAMembership));

        when(trustRepository.save(any(OrgTrustRelationshipEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrustRelationshipResponseDto response = trustService.revokeTrustRelationship(100L, adminA);

        assertNotNull(response);
        assertEquals(TrustStatus.REVOKED, response.getStatus());
        assertFalse(response.isActive());

        verify(trustRepository).save(activeTrust);
        verify(auditLogService).logPermissionChange(
                eq("TRUST_REVOKED"), eq("ORGANIZATION"), isNull(),
                isNull(), isNull(), isNull(), eq("ORG_A"), eq(adminA),
                isNull(), eq("ORG_B"), any(), isNull(), isNull()
        );
    }

    @Test
    @DisplayName("9b. revokeTrustRelationship - Suspended Admin: Throws AccessDeniedException")
    void revokeTrustRelationship_SuspendedAdmin_Failure() {
        OrgTrustRelationshipEntity activeTrust = OrgTrustRelationshipEntity.builder()
                .id(100L)
                .sourceOrganization(orgA)
                .targetOrganization(orgB)
                .status(TrustStatus.ACTIVE)
                .requestedBy(adminA)
                .approvedBy(adminB)
                .build();

        when(trustRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(activeTrust));

        UserOrganizationEntity suspendedAMembership = UserOrganizationEntity.builder()
                .user(adminA)
                .organization(orgA)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.SUSPENDED)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-admin-a", "ORG_A"))
                .thenReturn(Optional.of(suspendedAMembership));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                trustService.revokeTrustRelationship(100L, adminA));

        assertEquals("Admin privileges in either source or target organization required to revoke trust", ex.getMessage());
        verify(trustRepository, never()).save(any());
    }

    @Test
    @DisplayName("10. hasActiveTrust - Correctly evaluates active, non-expired trust relationships")
    void hasActiveTrust_Validation() {
        // Self check
        assertFalse(trustService.hasActiveTrust("ORG_A", "ORG_A"));
        assertFalse(trustService.hasActiveTrust(null, "ORG_B"));

        OrgTrustRelationshipEntity activeTrust = OrgTrustRelationshipEntity.builder()
                .id(100L)
                .sourceOrganization(orgA)
                .targetOrganization(orgB)
                .status(TrustStatus.ACTIVE)
                .build();

        when(trustRepository.findActiveTrustBetween(eq("ORG_A"), eq("ORG_B"), any(Instant.class)))
                .thenReturn(List.of(activeTrust));

        assertTrue(trustService.hasActiveTrust("ORG_A", "ORG_B"));

        when(trustRepository.findActiveTrustBetween(eq("ORG_A"), eq("ORG_C"), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        assertFalse(trustService.hasActiveTrust("ORG_A", "ORG_C"));
    }
}
