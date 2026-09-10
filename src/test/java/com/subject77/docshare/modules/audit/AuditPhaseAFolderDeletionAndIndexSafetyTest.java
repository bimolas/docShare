package com.subject77.docshare.modules.audit;

import com.subject77.docshare.modules.audit.entities.AccessLogEntity;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.AccessLogRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.folder.services.FolderService;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class AuditPhaseAFolderDeletionAndIndexSafetyTest {

    @Autowired
    private FolderService folderService;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private AccessLogRepository accessLogRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private UserEntity testUser;
    private OrganizationEntity testOrg;
    private FolderEntity testFolder;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        testUser = UserEntity.builder()
                .id("usr-audit-phaseA-" + uniqueSuffix)
                .email("audit.test." + uniqueSuffix + "@example.com")
                .role(Role.ROLE_USER)
                .build();
        testUser = userRepository.save(testUser);

        testOrg = OrganizationEntity.builder()
                .orgCode("ORG_AUDIT_" + uniqueSuffix)
                .name("Audit Test Org " + uniqueSuffix)
                .createdBy(testUser)
                .build();
        testOrg = organizationRepository.save(testOrg);

        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(testUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();
        userOrganizationRepository.save(membership);

        testFolder = FolderEntity.builder()
                .name("SafetyFolder_" + uniqueSuffix)
                .ownerOrg(testOrg)
                .createdBy(testUser)
                .parentFolder(null)
                .build();
        testFolder = folderRepository.save(testFolder);
    }

    @Test
    @DisplayName("Folder deletion succeeds with audit references; historical audit record is preserved")
    void testFolderDeletionPreservesAccessLogAndSetsFolderToNull() {
        // 1. Create a historical access log referencing the folder
        AccessLogEntity logEntry = AccessLogEntity.builder()
                .action(Action.VIEW_METADATA)
                .folder(testFolder)
                .actorUserId(testUser.getId())
                .accessorEmail(testUser.getEmail())
                .orgCode(testOrg.getOrgCode())
                .ipAddress("127.0.0.1")
                .userAgent("TestAgent/1.0")
                .details("Historical metadata view prior to deletion")
                .timestamp(Instant.now())
                .build();

        AccessLogEntity savedLog = accessLogRepository.saveAndFlush(logEntry);
        Long logId = savedLog.getId();
        Long folderId = testFolder.getId();

        assertThat(savedLog.getFolder()).isNotNull();
        assertThat(savedLog.getFolder().getId()).isEqualTo(folderId);

        // Clear persistence context to simulate subsequent transaction/request
        entityManager.flush();
        entityManager.clear();

        // 2. Delete the folder through FolderService
        folderService.deleteFolder(folderId, testUser);
        entityManager.flush();
        entityManager.clear();

        // 3. Verify folder is deleted
        assertThat(folderRepository.findById(folderId)).isEmpty();

        // 4. Verify historical access log is NOT deleted and folder reference handled safely
        AccessLogEntity retainedLog = accessLogRepository.findById(logId).orElse(null);
        assertThat(retainedLog).isNotNull();
        assertThat(retainedLog.getAction()).isEqualTo(Action.VIEW_METADATA);
        assertThat(retainedLog.getActorUserId()).isEqualTo(testUser.getId());
        assertThat(retainedLog.getDetails()).isEqualTo("Historical metadata view prior to deletion");
        // Due to ON DELETE SET NULL, the folder relationship gracefully resolves to null
        assertThat(retainedLog.getFolder()).isNull();
    }

    @Test
    @DisplayName("Verify AccessLogEntity declares folder_id and group_id composite time indexes")
    void testAccessLogEntityIndexMetadata() {
        Table tableAnn = AccessLogEntity.class.getAnnotation(Table.class);
        assertThat(tableAnn).isNotNull();

        List<String> indexColumnLists = Arrays.stream(tableAnn.indexes())
                .map(Index::columnList)
                .toList();

        assertThat(indexColumnLists).contains("folder_id, timestamp");
        assertThat(indexColumnLists).contains("group_id, timestamp");
    }

    @Test
    @DisplayName("Verify PermissionAuditLogEntity declares folder_id and group_id composite time indexes")
    void testPermissionAuditLogEntityIndexMetadata() {
        Table tableAnn = PermissionAuditLogEntity.class.getAnnotation(Table.class);
        assertThat(tableAnn).isNotNull();

        List<String> indexColumnLists = Arrays.stream(tableAnn.indexes())
                .map(Index::columnList)
                .toList();

        assertThat(indexColumnLists).contains("folder_id, timestamp");
        assertThat(indexColumnLists).contains("group_id, timestamp");
    }
}
