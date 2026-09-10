package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.entities.AccessRequestEntity;
import com.subject77.docshare.modules.permission.entities.AccessRequestStatus;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.repository.AccessRequestRepository;
import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.entities.WorkspaceType;
import com.subject77.docshare.modules.policy.repository.WorkspacePolicyRepository;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyService;
import com.subject77.docshare.modules.trust.dto.CreateTrustRequestDto;
import com.subject77.docshare.modules.trust.entities.OrgTrustRelationshipEntity;
import com.subject77.docshare.modules.trust.entities.TrustStatus;
import com.subject77.docshare.modules.trust.repository.OrgTrustRelationshipRepository;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/docshare",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driverClassName=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
public class Unit1AuthorityAndSecurityHardeningTest {

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private OrgTrustRelationshipRepository trustRelationshipRepository;

    @Autowired
    private OrgTrustRelationshipService trustRelationshipService;

    @Autowired
    private WorkspacePolicyService workspacePolicyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Test
    @DisplayName("DB Constraint 1: Partial unique index blocks duplicate pending access request for same (requester, document)")
    void testPartialUniqueIndex_PendingDocumentAccessRequest() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UserEntity user = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("req_doc_" + suffix + "@test.com")
                .firstName("Doc")
                .lastName("Requester")
                .role(Role.ROLE_USER)
                .build());

        DocumentEntity doc = documentRepository.save(DocumentEntity.builder()
                .fileName("test_" + suffix + ".pdf")
                .fileHash("hash_" + suffix)
                .contentType("application/pdf")
                .s3Bucket("docshare-bucket")
                .objectKey("test/" + suffix + ".pdf")
                .fileSize(100L)
                .uploadedBy(user)
                .status(DocumentStatus.ACTIVE)
                .build());

        AccessRequestEntity req1 = AccessRequestEntity.builder()
                .requester(user)
                .document(doc)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();
        accessRequestRepository.saveAndFlush(req1);

        AccessRequestEntity req2 = AccessRequestEntity.builder()
                .requester(user)
                .document(doc)
                .requestedPermissionLevel(PermissionLevel.DOWNLOAD)
                .status(AccessRequestStatus.PENDING)
                .build();

        assertThatThrownBy(() -> accessRequestRepository.saveAndFlush(req2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB Constraint 2: Partial unique index blocks duplicate pending access request for same (requester, folder)")
    void testPartialUniqueIndex_PendingFolderAccessRequest() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UserEntity user = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("req_folder_" + suffix + "@test.com")
                .firstName("Folder")
                .lastName("Requester")
                .role(Role.ROLE_USER)
                .build());

        FolderEntity folder = folderRepository.save(FolderEntity.builder()
                .name("Folder_" + suffix)
                .createdBy(user)
                .build());

        AccessRequestEntity req1 = AccessRequestEntity.builder()
                .requester(user)
                .folder(folder)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();
        accessRequestRepository.saveAndFlush(req1);

        AccessRequestEntity req2 = AccessRequestEntity.builder()
                .requester(user)
                .folder(folder)
                .requestedPermissionLevel(PermissionLevel.EDIT)
                .status(AccessRequestStatus.PENDING)
                .build();

        assertThatThrownBy(() -> accessRequestRepository.saveAndFlush(req2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB Constraint 3: Symmetric uniqueness blocks duplicate / reciprocal active/pending trust relationships")
    void testSymmetricUniqueness_OrgTrustRelationships() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String orgCodeA = "ORG_U1A_" + suffix;
        String orgCodeB = "ORG_U1B_" + suffix;

        UserEntity adminA = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_a_" + suffix + "@test.com")
                .firstName("Admin")
                .lastName("A")
                .role(Role.ROLE_ORG_ADMIN)
                .build());

        OrganizationEntity orgA = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(orgCodeA)
                .name("Org A " + suffix)
                .createdBy(adminA)
                .build());

        OrganizationEntity orgB = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(orgCodeB)
                .name("Org B " + suffix)
                .createdBy(adminA)
                .build());

        // A -> B trust created
        OrgTrustRelationshipEntity trust1 = OrgTrustRelationshipEntity.builder()
                .sourceOrganization(orgA)
                .targetOrganization(orgB)
                .status(TrustStatus.ACTIVE)
                .requestedBy(adminA)
                .build();
        trustRelationshipRepository.saveAndFlush(trust1);

        // B -> A reciprocal relationship must be rejected by symmetric unique index
        OrgTrustRelationshipEntity trust2 = OrgTrustRelationshipEntity.builder()
                .sourceOrganization(orgB)
                .targetOrganization(orgA)
                .status(TrustStatus.PENDING)
                .requestedBy(adminA)
                .build();

        assertThatThrownBy(() -> trustRelationshipRepository.saveAndFlush(trust2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Authority 1: Suspended Admin CANNOT create trust requests")
    void testSuspendedAdminCannotCreateTrustRequest() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String orgCodeA = "SUS_A_" + suffix;
        String orgCodeB = "SUS_B_" + suffix;

        UserEntity creator = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("creator_" + suffix + "@test.com")
                .firstName("Creator")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        OrganizationEntity orgA = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(orgCodeA)
                .name("Suspended Org A " + suffix)
                .createdBy(creator)
                .build());

        OrganizationEntity orgB = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(orgCodeB)
                .name("Suspended Org B " + suffix)
                .createdBy(creator)
                .build());

        UserEntity suspendedAdmin = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("sus_admin_" + suffix + "@test.com")
                .firstName("Suspended")
                .lastName("Admin")
                .role(Role.ROLE_ORG_ADMIN)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(suspendedAdmin)
                .organization(orgA)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.SUSPENDED)
                .build());

        CreateTrustRequestDto dto = CreateTrustRequestDto.builder()
                .sourceOrgCode(orgCodeA)
                .targetOrgCode(orgCodeB)
                .build();

        assertThatThrownBy(() -> trustRelationshipService.createTrustRequest(dto, suspendedAdmin))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Authority 2: Workspace Manager CANNOT change organization workspace policy; Org Admin CAN")
    void testWorkspaceManagerCannotChangePolicy_OrgAdminCan() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String orgCode = "POL_ORG_" + suffix;

        UserEntity orgAdmin = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_pol_" + suffix + "@test.com")
                .firstName("Admin")
                .lastName("Pol")
                .role(Role.ROLE_ORG_ADMIN)
                .build());

        OrganizationEntity org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(orgCode)
                .name("Policy Org " + suffix)
                .createdBy(orgAdmin)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(orgAdmin)
                .organization(org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build());

        UserEntity wsManager = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("ws_mgr_" + suffix + "@test.com")
                .firstName("Ws")
                .lastName("Manager")
                .role(Role.ROLE_USER)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(wsManager)
                .organization(org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        FolderEntity rootFolder = folderRepository.save(FolderEntity.builder()
                .name("RootFolder_" + suffix)
                .ownerOrg(org)
                .createdBy(orgAdmin)
                .build());

        ProjectWorkspaceEntity workspace = projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name("Project Workspace " + suffix)
                .organization(org)
                .rootFolder(rootFolder)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(orgAdmin)
                .build());

        // 1. Workspace Manager attempts to mutate organization policy -> MUST FAIL
        assertThatThrownBy(() -> workspacePolicyService.updateOrganizationPolicy(
                orgCode,
                ExternalSharingMode.ON,
                DiscoverabilityMode.DISCOVERABLE,
                true,
                false,
                false,
                wsManager))
                .isInstanceOf(AccessDeniedException.class);

        // 2. Org Admin attempts to mutate organization policy -> MUST SUCCEED
        WorkspacePolicyEntity updated = workspacePolicyService.updateOrganizationPolicy(
                orgCode,
                ExternalSharingMode.ON,
                DiscoverabilityMode.DISCOVERABLE,
                true,
                false,
                false,
                orgAdmin);

        assertThat(updated).isNotNull();
        assertThat(updated.getExternalSharingMode()).isEqualTo(ExternalSharingMode.ON);
        assertThat(updated.getAllowAccessRequests()).isTrue();
    }
}
