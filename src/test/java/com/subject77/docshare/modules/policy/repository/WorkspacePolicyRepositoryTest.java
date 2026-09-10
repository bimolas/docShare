package com.subject77.docshare.modules.policy.repository;

import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.entities.WorkspaceType;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class WorkspacePolicyRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private WorkspacePolicyRepository workspacePolicyRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;
    private OrganizationEntity testOrg;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("user-" + UUID.randomUUID() + "@test.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.ROLE_USER)
                .build();
        userRepository.save(testUser);

        testOrg = OrganizationEntity.builder()
                .orgCode("ORG-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Test Policy Organization")
                .createdBy(testUser)
                .build();
        organizationRepository.save(testOrg);
        entityManager.flush();
    }

    @Test
    @DisplayName("Should save Organization policy with defaults (OFF, DISCOVERABLE)")
    void shouldSaveOrganizationPolicyWithDefaults() {
        WorkspacePolicyEntity policy = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.ORGANIZATION)
                .organization(testOrg)
                .build();

        WorkspacePolicyEntity saved = workspacePolicyRepository.save(policy);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getWorkspaceType()).isEqualTo(WorkspaceType.ORGANIZATION);
        assertThat(saved.getOrganization().getOrgCode()).isEqualTo(testOrg.getOrgCode());
        assertThat(saved.getUser()).isNull();
        assertThat(saved.getExternalSharingMode()).isEqualTo(ExternalSharingMode.OFF);
        assertThat(saved.getDiscoverabilityMode()).isEqualTo(DiscoverabilityMode.DISCOVERABLE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should save Personal policy with defaults (OFF, PRIVATE)")
    void shouldSavePersonalPolicyWithDefaults() {
        WorkspacePolicyEntity policy = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.PERSONAL)
                .user(testUser)
                .build();

        WorkspacePolicyEntity saved = workspacePolicyRepository.save(policy);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getWorkspaceType()).isEqualTo(WorkspaceType.PERSONAL);
        assertThat(saved.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(saved.getOrganization()).isNull();
        assertThat(saved.getExternalSharingMode()).isEqualTo(ExternalSharingMode.OFF);
        assertThat(saved.getDiscoverabilityMode()).isEqualTo(DiscoverabilityMode.PRIVATE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should find policy by organization orgCode")
    void shouldFindPolicyByOrgCode() {
        WorkspacePolicyEntity policy = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.ORGANIZATION)
                .organization(testOrg)
                .build();

        workspacePolicyRepository.save(policy);
        entityManager.flush();

        Optional<WorkspacePolicyEntity> found = workspacePolicyRepository.findByOrganization_OrgCode(testOrg.getOrgCode());

        assertThat(found).isPresent();
        assertThat(found.get().getOrganization().getOrgCode()).isEqualTo(testOrg.getOrgCode());
        assertThat(found.get().getWorkspaceType()).isEqualTo(WorkspaceType.ORGANIZATION);
    }

    @Test
    @DisplayName("Should find policy by user id")
    void shouldFindPolicyByUserId() {
        WorkspacePolicyEntity policy = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.PERSONAL)
                .user(testUser)
                .build();

        workspacePolicyRepository.save(policy);
        entityManager.flush();

        Optional<WorkspacePolicyEntity> found = workspacePolicyRepository.findByUser_Id(testUser.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getUser().getId()).isEqualTo(testUser.getId());
        assertThat(found.get().getWorkspaceType()).isEqualTo(WorkspaceType.PERSONAL);
    }

    @Test
    @DisplayName("Should enforce uniqueness per Organization")
    void shouldEnforceUniquenessPerOrganization() {
        WorkspacePolicyEntity policy1 = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.ORGANIZATION)
                .organization(testOrg)
                .build();
        workspacePolicyRepository.save(policy1);
        entityManager.flush();

        WorkspacePolicyEntity policy2 = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.ORGANIZATION)
                .organization(testOrg)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            workspacePolicyRepository.save(policy2);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("Should enforce uniqueness per User")
    void shouldEnforceUniquenessPerUser() {
        WorkspacePolicyEntity policy1 = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.PERSONAL)
                .user(testUser)
                .build();
        workspacePolicyRepository.save(policy1);
        entityManager.flush();

        WorkspacePolicyEntity policy2 = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.PERSONAL)
                .user(testUser)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            workspacePolicyRepository.save(policy2);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("Should fail validation if personal policy has organization attached")
    void shouldFailValidationIfPersonalPolicyHasOrg() {
        WorkspacePolicyEntity policy = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.PERSONAL)
                .user(testUser)
                .organization(testOrg)
                .build();

        Exception exception = assertThrows(Exception.class, () -> {
            workspacePolicyRepository.save(policy);
        });
        assertThat(exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage())
                .contains("Personal workspace policy must be associated with a User and no Organization");
    }

    @Test
    @DisplayName("Should update policy mode settings")
    void shouldUpdatePolicyModes() {
        WorkspacePolicyEntity policy = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.PERSONAL)
                .user(testUser)
                .build();
        WorkspacePolicyEntity saved = workspacePolicyRepository.save(policy);
        entityManager.flush();

        saved.setExternalSharingMode(ExternalSharingMode.ON);
        saved.setDiscoverabilityMode(DiscoverabilityMode.TRUSTED_ONLY);
        workspacePolicyRepository.save(saved);
        entityManager.flush();

        WorkspacePolicyEntity updated = workspacePolicyRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getExternalSharingMode()).isEqualTo(ExternalSharingMode.ON);
        assertThat(updated.getDiscoverabilityMode()).isEqualTo(DiscoverabilityMode.TRUSTED_ONLY);
    }
}
