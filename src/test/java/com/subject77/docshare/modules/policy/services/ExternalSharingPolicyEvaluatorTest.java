package com.subject77.docshare.modules.policy.services;

import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExternalSharingPolicyEvaluatorTest {

        @Mock
        private WorkspacePolicyService workspacePolicyService;

        @Mock
        private OrgTrustRelationshipService orgTrustRelationshipService;

        @Mock
        private UserRepository userRepository;

        @Mock
        private OrganizationRepository organizationRepository;

        @Mock
        private UserOrganizationRepository userOrganizationRepository;

        @InjectMocks
        private ExternalSharingPolicyEvaluator evaluator;

        private OrganizationEntity acmeOrg;
        private OrganizationEntity betaOrg;

        private UserEntity acmeUser;
        private UserEntity betaUser;
        private UserEntity externalAffiliatedUser;

        private FolderEntity acmeFolder;
        private FolderEntity personalFolder;

        private WorkspacePolicyEntity acmePolicyOn;
        private WorkspacePolicyEntity acmePolicyOff;
        private WorkspacePolicyEntity betaPolicyOn;
        private WorkspacePolicyEntity betaPolicyOff;
        private WorkspacePolicyEntity personalPolicyOn;
        private WorkspacePolicyEntity personalPolicyOff;

        @BeforeEach
        void setUp() {
                acmeOrg = OrganizationEntity.builder().orgCode("ACME").name("ACME Corp").build();
                betaOrg = OrganizationEntity.builder().orgCode("Beta").name("Beta Corp").build();

                acmeUser = UserEntity.builder().id("user-acme").email("john@acme.com").organization(acmeOrg).build();
                betaUser = UserEntity.builder().id("user-beta").email("jane@beta.com").organization(betaOrg).build();
                externalAffiliatedUser = UserEntity.builder().id("user-affiliated").email("user@partner.com").organization(betaOrg).build();

                acmeFolder = FolderEntity.builder().id(100L).name("ACME Docs").ownerOrg(acmeOrg).createdBy(acmeUser)
                                .build();
                personalFolder = FolderEntity.builder().id(200L).name("John Personal").ownerOrg(null)
                                .createdBy(acmeUser).build();

                acmePolicyOn = WorkspacePolicyEntity.builder().id(10L).organization(acmeOrg)
                                .externalSharingMode(ExternalSharingMode.ON)
                                .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE).build();
                acmePolicyOff = WorkspacePolicyEntity.builder().id(11L).organization(acmeOrg)
                                .externalSharingMode(ExternalSharingMode.OFF)
                                .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE).build();

                betaPolicyOn = WorkspacePolicyEntity.builder().id(20L).organization(betaOrg)
                                .externalSharingMode(ExternalSharingMode.ON)
                                .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE).build();
                betaPolicyOff = WorkspacePolicyEntity.builder().id(21L).organization(betaOrg)
                                .externalSharingMode(ExternalSharingMode.OFF)
                                .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE).build();

                personalPolicyOn = WorkspacePolicyEntity.builder().id(30L).user(acmeUser)
                                .externalSharingMode(ExternalSharingMode.ON)
                                .discoverabilityMode(DiscoverabilityMode.PRIVATE).build();
                personalPolicyOff = WorkspacePolicyEntity.builder().id(31L).user(acmeUser)
                                .externalSharingMode(ExternalSharingMode.OFF)
                                .discoverabilityMode(DiscoverabilityMode.PRIVATE).build();
        }

        @Test
        @DisplayName("1. Internal share succeeds")
        void testInternalShareSucceeds() {
                when(userRepository.findByEmail("john@acme.com")).thenReturn(Optional.of(acmeUser));

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(100L)
                                .targetUserEmail("john@acme.com")
                                .build();

                assertDoesNotThrow(() -> evaluator.evaluateAndValidateSharingPolicy(acmeFolder, null, request, null));
                verify(workspacePolicyService, never()).getOrCreateOrganizationPolicy(any(OrganizationEntity.class));
                verify(orgTrustRelationshipService, never()).hasActiveTrust(any(), any());
        }

        @Test
        @DisplayName("2. External source policy OFF -> denied")
        void testExternalSourcePolicyOffDenied() {
                when(userRepository.findByEmail("jane@beta.com")).thenReturn(Optional.of(betaUser));
                when(workspacePolicyService.getOrCreateOrganizationPolicy(acmeOrg)).thenReturn(acmePolicyOff);

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(100L)
                                .targetUserEmail("jane@beta.com")
                                .build();

                AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                                () -> evaluator.evaluateAndValidateSharingPolicy(acmeFolder, null, request, null));
                assertTrue(ex.getMessage().contains("disabled for the source workspace"));
        }

        @Test
        @DisplayName("3. External target policy OFF -> denied")
        void testExternalTargetPolicyOffDenied() {
                when(userRepository.findByEmail("jane@beta.com")).thenReturn(Optional.of(betaUser));
                when(organizationRepository.findByOrgCode("Beta")).thenReturn(Optional.of(betaOrg));
                when(workspacePolicyService.getOrCreateOrganizationPolicy(acmeOrg)).thenReturn(acmePolicyOn);
                when(workspacePolicyService.getOrCreateOrganizationPolicy(betaOrg)).thenReturn(betaPolicyOff);

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(100L)
                                .targetUserEmail("jane@beta.com")
                                .build();

                AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                                () -> evaluator.evaluateAndValidateSharingPolicy(acmeFolder, null, request, null));
                assertTrue(ex.getMessage().contains("disabled for target organization: Beta"));
        }

        @Test
        @DisplayName("4. Cross-org without ACTIVE trust -> denied")
        void testCrossOrgWithoutActiveTrustDenied() {
                when(userRepository.findByEmail("jane@beta.com")).thenReturn(Optional.of(betaUser));
                when(organizationRepository.findByOrgCode("Beta")).thenReturn(Optional.of(betaOrg));
                when(workspacePolicyService.getOrCreateOrganizationPolicy(acmeOrg)).thenReturn(acmePolicyOn);
                when(workspacePolicyService.getOrCreateOrganizationPolicy(betaOrg)).thenReturn(betaPolicyOn);
                when(orgTrustRelationshipService.hasActiveTrust("ACME", "Beta")).thenReturn(false);

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(100L)
                                .targetUserEmail("jane@beta.com")
                                .build();

                AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                                () -> evaluator.evaluateAndValidateSharingPolicy(acmeFolder, null, request, null));
                assertTrue(ex.getMessage().contains("No active trust relationship"));
        }

        @Test
        @DisplayName("5. Cross-org with both policies ON + ACTIVE trust -> allowed")
        void testCrossOrgWithBothPoliciesOnAndActiveTrustAllowed() {
                when(userRepository.findByEmail("jane@beta.com")).thenReturn(Optional.of(betaUser));
                when(organizationRepository.findByOrgCode("Beta")).thenReturn(Optional.of(betaOrg));
                when(workspacePolicyService.getOrCreateOrganizationPolicy(acmeOrg)).thenReturn(acmePolicyOn);
                when(workspacePolicyService.getOrCreateOrganizationPolicy(betaOrg)).thenReturn(betaPolicyOn);
                when(orgTrustRelationshipService.hasActiveTrust("ACME", "Beta")).thenReturn(true);

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(100L)
                                .targetUserEmail("jane@beta.com")
                                .build();

                assertDoesNotThrow(() -> evaluator.evaluateAndValidateSharingPolicy(acmeFolder, null, request, null));
        }

        @Test
        @DisplayName("6. Personal Space externalSharing OFF -> denied")
        void testPersonalSpaceExternalSharingOffDenied() {
                when(userRepository.findByEmail("jane@beta.com")).thenReturn(Optional.of(betaUser));
                when(workspacePolicyService.getOrCreatePersonalPolicy(acmeUser)).thenReturn(personalPolicyOff);

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(200L)
                                .targetUserEmail("jane@beta.com")
                                .build();

                AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                                () -> evaluator.evaluateAndValidateSharingPolicy(personalFolder, null, request, null));
                assertTrue(ex.getMessage().contains("disabled for the source workspace"));
        }

        @Test
        @DisplayName("7. Personal Space externalSharing ON -> allowed when target checks pass")
        void testPersonalSpaceExternalSharingOnAllowedWhenTargetChecksPass() {
                when(userRepository.findByEmail("jane@beta.com")).thenReturn(Optional.of(betaUser));
                when(organizationRepository.findByOrgCode("Beta")).thenReturn(Optional.of(betaOrg));
                when(workspacePolicyService.getOrCreatePersonalPolicy(acmeUser)).thenReturn(personalPolicyOn);
                when(workspacePolicyService.getOrCreateOrganizationPolicy(betaOrg)).thenReturn(betaPolicyOn);

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(200L)
                                .targetUserEmail("jane@beta.com")
                                .build();

                assertDoesNotThrow(
                                () -> evaluator.evaluateAndValidateSharingPolicy(personalFolder, null, request, null));
                verify(orgTrustRelationshipService, never()).hasActiveTrust(any(), any());
        }

        @Test
        @DisplayName("8. Selected external member uses same policy gate")
        void testSelectedExternalMemberUsesSamePolicyGate() {
                when(userRepository.findByEmail("jane@beta.com")).thenReturn(Optional.of(betaUser));
                when(organizationRepository.findByOrgCode("Beta")).thenReturn(Optional.of(betaOrg));
                when(workspacePolicyService.getOrCreateOrganizationPolicy(acmeOrg)).thenReturn(acmePolicyOn);
                when(workspacePolicyService.getOrCreateOrganizationPolicy(betaOrg)).thenReturn(betaPolicyOn);
                when(orgTrustRelationshipService.hasActiveTrust("ACME", "Beta")).thenReturn(false);

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(100L)
                                .targetUserEmail("jane@beta.com")
                                .build();

                AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                                () -> evaluator.evaluateAndValidateSharingPolicy(acmeFolder, null, request, null));
                assertTrue(ex.getMessage().contains("No active trust relationship"));
        }

        @Test
        @DisplayName("9. Active membership in source org -> recognized as internal")
        void testActiveMembershipRecognizedAsInternal() {
                when(userRepository.findByEmail("user@partner.com")).thenReturn(Optional.of(externalAffiliatedUser));
                when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-affiliated", "ACME", MembershipStatus.ACTIVE))
                                .thenReturn(true);

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(100L)
                                .targetUserEmail("user@partner.com")
                                .build();

                assertDoesNotThrow(() -> evaluator.evaluateAndValidateSharingPolicy(acmeFolder, null, request, null));
                verify(workspacePolicyService, never()).getOrCreateOrganizationPolicy(any(OrganizationEntity.class));
                verify(orgTrustRelationshipService, never()).hasActiveTrust(any(), any());
        }

        @Test
        @DisplayName("10. Removed membership in source org -> NOT recognized as internal")
        void testRemovedMembershipNotRecognizedAsInternal() {
                when(userRepository.findByEmail("user@partner.com")).thenReturn(Optional.of(externalAffiliatedUser));
                when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-affiliated", "ACME", MembershipStatus.ACTIVE))
                                .thenReturn(false);
                when(workspacePolicyService.getOrCreateOrganizationPolicy(acmeOrg)).thenReturn(acmePolicyOff);

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(100L)
                                .targetUserEmail("user@partner.com")
                                .build();

                AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                                () -> evaluator.evaluateAndValidateSharingPolicy(acmeFolder, null, request, null));
                assertTrue(ex.getMessage().contains("disabled for the source workspace"));
        }

        @Test
        @DisplayName("11. Suspended membership in source org -> NOT recognized as internal")
        void testSuspendedMembershipNotRecognizedAsInternal() {
                when(userRepository.findByEmail("user@partner.com")).thenReturn(Optional.of(externalAffiliatedUser));
                when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-affiliated", "ACME", MembershipStatus.ACTIVE))
                                .thenReturn(false);
                when(workspacePolicyService.getOrCreateOrganizationPolicy(acmeOrg)).thenReturn(acmePolicyOff);

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(100L)
                                .targetUserEmail("user@partner.com")
                                .build();

                AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                                () -> evaluator.evaluateAndValidateSharingPolicy(acmeFolder, null, request, null));
                assertTrue(ex.getMessage().contains("disabled for the source workspace"));
        }

        @Test
        @DisplayName("12. Invited membership in source org -> NOT recognized as internal")
        void testInvitedMembershipNotRecognizedAsInternal() {
                when(userRepository.findByEmail("user@partner.com")).thenReturn(Optional.of(externalAffiliatedUser));
                when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-affiliated", "ACME", MembershipStatus.ACTIVE))
                                .thenReturn(false);
                when(workspacePolicyService.getOrCreateOrganizationPolicy(acmeOrg)).thenReturn(acmePolicyOff);

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(100L)
                                .targetUserEmail("user@partner.com")
                                .build();

                AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                                () -> evaluator.evaluateAndValidateSharingPolicy(acmeFolder, null, request, null));
                assertTrue(ex.getMessage().contains("disabled for the source workspace"));
        }

        @Test
        @DisplayName("13. Regression: Runtime permission check for active member remains valid")
        void testRuntimePermissionCheckActiveMemberValid() {
                PermissionEntity permission = PermissionEntity.builder()
                                .folder(acmeFolder)
                                .targetUserEmail("john@acme.com")
                                .build();

                boolean valid = evaluator.isExternalPermissionRuntimeValid(permission, acmeUser);
                assertTrue(valid);
        }

        @Test
        @DisplayName("14. Security regression: removed member must go through external trust check and is denied when trust is missing")
        void testSecurityRegressionRemovedMemberSubjectToExternalTrustCheck() {
                when(userRepository.findByEmail("user@partner.com")).thenReturn(Optional.of(externalAffiliatedUser));
                when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-affiliated", "ACME", MembershipStatus.ACTIVE))
                                .thenReturn(false);
                when(organizationRepository.findByOrgCode("Beta")).thenReturn(Optional.of(betaOrg));
                when(workspacePolicyService.getOrCreateOrganizationPolicy(acmeOrg)).thenReturn(acmePolicyOn);
                when(workspacePolicyService.getOrCreateOrganizationPolicy(betaOrg)).thenReturn(betaPolicyOn);
                when(orgTrustRelationshipService.hasActiveTrust("ACME", "Beta")).thenReturn(false);

                PermissionRequestDto request = PermissionRequestDto.builder()
                                .permissionType(PermissionType.ALLOW)
                                .permissionLevel(PermissionLevel.VIEW)
                                .folderId(100L)
                                .targetUserEmail("user@partner.com")
                                .build();

                AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                                () -> evaluator.evaluateAndValidateSharingPolicy(acmeFolder, null, request, null));
                assertTrue(ex.getMessage().contains("No active trust relationship"));
        }

        @Test
        @DisplayName("15. Security regression: Runtime check denies removed member when external trust is revoked")
        void testSecurityRegressionRuntimeCheckDeniesRemovedMemberWhenTrustRevoked() {
                when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-affiliated", "ACME", MembershipStatus.ACTIVE))
                                .thenReturn(false);
                when(organizationRepository.findByOrgCode("Beta")).thenReturn(Optional.of(betaOrg));
                when(workspacePolicyService.getOrCreateOrganizationPolicy(acmeOrg)).thenReturn(acmePolicyOn);
                when(workspacePolicyService.getOrCreateOrganizationPolicy(betaOrg)).thenReturn(betaPolicyOn);
                when(orgTrustRelationshipService.hasActiveTrust("ACME", "Beta")).thenReturn(false);

                PermissionEntity permission = PermissionEntity.builder()
                                .folder(acmeFolder)
                                .targetUserEmail("user@partner.com")
                                .build();

                boolean valid = evaluator.isExternalPermissionRuntimeValid(permission, externalAffiliatedUser);
                assertFalse(valid, "Runtime check must return false when removed member is evaluated as external and trust is missing");
        }
}
