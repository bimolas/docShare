package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.document.dto.DocumentResponse;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.dto.FolderContentResponseDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.folder.services.FolderService;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.dto.AccessRequestActionDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestResponseDto;
import com.subject77.docshare.modules.permission.entities.*;
import com.subject77.docshare.modules.permission.repository.AccessRequestRepository;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.entities.WorkspaceType;
import com.subject77.docshare.modules.policy.repository.WorkspacePolicyRepository;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/docshare",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driverClassName=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
@Transactional
public class RealExclusionDescendantsIntegrationTest {

    @Autowired
    private FolderService folderService;

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private PermissionEvaluatorService permissionEvaluatorService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private WorkspacePolicyRepository workspacePolicyRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private UserEntity owner;
    private UserEntity recipient;
    private OrganizationEntity org;
    private FolderEntity sharedFolder;
    private FolderEntity allowedSubFolder;
    private FolderEntity excludedSubFolder;
    private DocumentEntity allowedDoc;
    private DocumentEntity excludedDoc;
    private WorkspacePolicyEntity orgPolicy;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        owner = userRepository.save(UserEntity.builder()
                .id("owner_" + suffix)
                .email("owner_" + suffix + "@test.com")
                .firstName("Owner")
                .lastName("Test")
                .role(Role.ROLE_ORG_ADMIN)
                .build());

        org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORG_" + suffix)
                .name("Org " + suffix)
                .createdBy(owner)
                .build());

        owner.setOrganization(org);
        owner = userRepository.save(owner);

        recipient = userRepository.save(UserEntity.builder()
                .id("recipient_" + suffix)
                .email("recipient_" + suffix + "@test.com")
                .firstName("Recipient")
                .lastName("Test")
                .role(Role.ROLE_USER)
                .organization(org)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(owner)
                .organization(org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(recipient)
                .organization(org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        // Default Organization Policy with Access Requests initially OFF
        orgPolicy = workspacePolicyRepository.findByOrganization_OrgCode(org.getOrgCode())
                .orElseGet(() -> workspacePolicyRepository.save(WorkspacePolicyEntity.builder()
                        .workspaceType(WorkspaceType.ORGANIZATION)
                        .organization(org)
                        .externalSharingMode(ExternalSharingMode.OFF)
                        .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE)
                        .allowAccessRequests(false)
                        .requireRequestReason(false)
                        .allowExternalAccessRequests(false)
                        .build()));
        orgPolicy.setAllowAccessRequests(false);
        workspacePolicyRepository.save(orgPolicy);

        // Create Shared Folder
        sharedFolder = folderRepository.save(FolderEntity.builder()
                .name("SharedRoot_" + suffix)
                .subspaceType(SubspaceType.ORGANIZATION_GENERAL)
                .ownerOrg(org)
                .createdBy(owner)
                .build());

        // Create Allowed Subfolder
        allowedSubFolder = folderRepository.save(FolderEntity.builder()
                .name("AllowedSub_" + suffix)
                .subspaceType(SubspaceType.ORGANIZATION_GENERAL)
                .parentFolder(sharedFolder)
                .ownerOrg(org)
                .createdBy(owner)
                .build());

        // Create Excluded Subfolder
        excludedSubFolder = folderRepository.save(FolderEntity.builder()
                .name("ExcludedSub_" + suffix)
                .subspaceType(SubspaceType.ORGANIZATION_GENERAL)
                .parentFolder(sharedFolder)
                .ownerOrg(org)
                .createdBy(owner)
                .build());

        // Create Allowed Document
        allowedDoc = documentRepository.save(DocumentEntity.builder()
                .fileName("allowed_doc.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .fileHash("hash_allowed_" + suffix)
                .s3Bucket("secret-s3-bucket")
                .objectKey("docs/internal/allowed_" + suffix + ".pdf")
                .status(DocumentStatus.ACTIVE)
                .folder(sharedFolder)
                .ownerOrg(org)
                .uploadedBy(owner)
                .build());

        // Create Excluded Document
        excludedDoc = documentRepository.save(DocumentEntity.builder()
                .fileName("confidential_excluded.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .fileHash("hash_excluded_" + suffix)
                .s3Bucket("secret-s3-bucket")
                .objectKey("docs/internal/secret_keys_" + suffix + ".pdf")
                .status(DocumentStatus.ACTIVE)
                .folder(sharedFolder)
                .ownerOrg(org)
                .uploadedBy(owner)
                .build());

        // 1. Grant ALLOW VIEW on sharedFolder to recipient
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail(recipient.getEmail())
                .targetOrgCode(org.getOrgCode())
                .folder(sharedFolder)
                .grantedBy(owner)
                .build());

        // 2. Grant explicit EXCLUDE on excludedDoc to recipient
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.EXCLUDE)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail(recipient.getEmail())
                .targetOrgCode(org.getOrgCode())
                .document(excludedDoc)
                .documentFileHash(excludedDoc.getFileHash())
                .grantedBy(owner)
                .build());

        // 3. Grant explicit EXCLUDE on excludedSubFolder to recipient
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.EXCLUDE)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail(recipient.getEmail())
                .targetOrgCode(org.getOrgCode())
                .folder(excludedSubFolder)
                .grantedBy(owner)
                .build());
    }

    @Test
    @DisplayName("Policy OFF: Excluded child document and subfolder are completely hidden from API response")
    void testAccessRequestsOff_ExcludedDescendantsHidden() {
        orgPolicy.setAllowAccessRequests(false);
        workspacePolicyRepository.save(orgPolicy);

        FolderContentResponseDto contents = folderService.getFolderContents(sharedFolder.getId(), recipient);

        // Allowed items are present
        assertThat(contents.getDocuments())
                .extracting(DocumentResponse::getId)
                .contains(allowedDoc.getId());
        assertThat(contents.getSubFolders())
                .extracting(FolderResponseDto::getId)
                .contains(allowedSubFolder.getId());

        // Excluded items MUST be completely absent (zero metadata leak)
        assertThat(contents.getDocuments())
                .extracting(DocumentResponse::getId)
                .doesNotContain(excludedDoc.getId());
        assertThat(contents.getSubFolders())
                .extracting(FolderResponseDto::getId)
                .doesNotContain(excludedSubFolder.getId());
    }

    @Test
    @DisplayName("Policy ON: Excluded child appears as LOCKED with strictly sanitized metadata")
    void testAccessRequestsOn_ExcludedDescendantsLockedAndSanitized() {
        orgPolicy.setAllowAccessRequests(true);
        workspacePolicyRepository.save(orgPolicy);

        FolderContentResponseDto contents = folderService.getFolderContents(sharedFolder.getId(), recipient);

        // Excluded Document appears in listing
        Optional<DocumentResponse> lockedDocOpt = contents.getDocuments().stream()
                .filter(d -> d.getId().equals(excludedDoc.getId()))
                .findFirst();

        assertThat(lockedDocOpt).isPresent();
        DocumentResponse lockedDoc = lockedDocOpt.get();

        // Verify discovery flags
        assertThat(lockedDoc.getAccessStatus()).isEqualTo("LOCKED");
        assertThat(lockedDoc.getIsLocked()).isTrue();
        assertThat(lockedDoc.getRequestStatus()).isEqualTo("NONE");
        assertThat(lockedDoc.getCanRequestAccess()).isTrue();
        assertThat(lockedDoc.getFileName()).isEqualTo("confidential_excluded.pdf");

        // CRITICAL: Sensitive infrastructure & PII metadata must be NULL
        assertThat(lockedDoc.getS3Bucket()).isNull();
        assertThat(lockedDoc.getObjectKey()).isNull();
        assertThat(lockedDoc.getFileHash()).isNull();
        assertThat(lockedDoc.getFileSize()).isNull();
        assertThat(lockedDoc.getContentType()).isNull();
        assertThat(lockedDoc.getUploadedByUserId()).isNull();
        assertThat(lockedDoc.getUploadedByUserEmail()).isNull();
        assertThat(lockedDoc.getVersion()).isNull();
        assertThat(lockedDoc.getStatus()).isNull();

        // Excluded Subfolder appears in listing as LOCKED
        Optional<FolderResponseDto> lockedFolderOpt = contents.getSubFolders().stream()
                .filter(f -> f.getId().equals(excludedSubFolder.getId()))
                .findFirst();

        assertThat(lockedFolderOpt).isPresent();
        FolderResponseDto lockedFolder = lockedFolderOpt.get();
        assertThat(lockedFolder.getAccessStatus()).isEqualTo("LOCKED");
        assertThat(lockedFolder.getIsLocked()).isTrue();
        assertThat(lockedFolder.getRequestStatus()).isEqualTo("NONE");
        assertThat(lockedFolder.getCanRequestAccess()).isTrue();
        assertThat(lockedFolder.getName()).isEqualTo(excludedSubFolder.getName());
        assertThat(lockedFolder.getCreatedByUserId()).isNull();
        assertThat(lockedFolder.getOwnerOrgCode()).isNull();
    }

    @Test
    @DisplayName("Request Lifecycle: Submit request transitions to PENDING, Approval revokes EXCLUDE and restores ACCESSIBLE")
    void testRequestLifecycle_ApprovalRevokesExcludeAndGrantsAccess() {
        orgPolicy.setAllowAccessRequests(true);
        workspacePolicyRepository.save(orgPolicy);

        // 1. Submit Access Request
        AccessRequestCreateDto createDto = AccessRequestCreateDto.builder()
                .documentId(excludedDoc.getId())
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .reason("Need access for audit review")
                .build();

        AccessRequestResponseDto requestRes = accessRequestService.createAccessRequest(createDto, recipient);
        assertThat(requestRes.getStatus()).isEqualTo(AccessRequestStatus.PENDING);

        // 2. Folder listing now reflects PENDING request state
        FolderContentResponseDto contentsPending = folderService.getFolderContents(sharedFolder.getId(), recipient);
        DocumentResponse docPending = contentsPending.getDocuments().stream()
                .filter(d -> d.getId().equals(excludedDoc.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(docPending.getAccessStatus()).isEqualTo("LOCKED");
        assertThat(docPending.getIsLocked()).isTrue();
        assertThat(docPending.getRequestStatus()).isEqualTo("PENDING");
        assertThat(docPending.getCanRequestAccess()).isFalse();
        assertThat(docPending.getPendingRequestId()).isEqualTo(requestRes.getId());

        // 3. Reviewer (owner) approves Access Request
        AccessRequestActionDto actionDto = AccessRequestActionDto.builder()
                .reviewerNote("Approved for audit")
                .build();
        AccessRequestResponseDto approvedRes = accessRequestService.approveAccessRequest(requestRes.getId(), actionDto, owner);
        assertThat(approvedRes.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);

        // 4. Verify explicit EXCLUDE was REVOKED
        List<PermissionEntity> exclusions = permissionRepository.findByTargetUserEmailIgnoreCaseAndDocument_IdAndRevokedFalse(
                recipient.getEmail(), excludedDoc.getId());
        assertThat(exclusions.stream().noneMatch(p -> p.getPermissionType() == PermissionType.EXCLUDE)).isTrue();

        // 5. Verify evaluator now grants view access
        boolean canView = permissionEvaluatorService.canViewDocument(recipient, excludedDoc);
        assertThat(canView).isTrue();

        // 6. Folder listing now reflects ACCESSIBLE with full metadata
        FolderContentResponseDto contentsApproved = folderService.getFolderContents(sharedFolder.getId(), recipient);
        DocumentResponse docApproved = contentsApproved.getDocuments().stream()
                .filter(d -> d.getId().equals(excludedDoc.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(docApproved.getAccessStatus()).isEqualTo("ACCESSIBLE");
        assertThat(docApproved.getIsLocked()).isFalse();
        assertThat(docApproved.getS3Bucket()).isEqualTo("secret-s3-bucket");
        assertThat(docApproved.getObjectKey()).isEqualTo(excludedDoc.getObjectKey());
        assertThat(docApproved.getFileHash()).isEqualTo(excludedDoc.getFileHash());
    }
}
