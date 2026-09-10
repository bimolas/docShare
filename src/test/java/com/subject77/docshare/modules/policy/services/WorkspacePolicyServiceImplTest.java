package com.subject77.docshare.modules.policy.services;

import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.entities.WorkspaceType;
import com.subject77.docshare.modules.policy.repository.WorkspacePolicyRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class WorkspacePolicyServiceImplTest {

    @Autowired
    private WorkspacePolicyService workspacePolicyService;

    @Autowired
    private WorkspacePolicyRepository workspacePolicyRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private UserEntity testUser;
    private OrganizationEntity testOrg;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("service-user-" + UUID.randomUUID() + "@test.com")
                .firstName("Jane")
                .lastName("Doe")
                .role(Role.ROLE_USER)
                .build();
        userRepository.save(testUser);

        testOrg = OrganizationEntity.builder()
                .orgCode("ORG-SVC-" + UUID.randomUUID().toString().substring(0, 8))
                .name("Service Policy Organization")
                .createdBy(testUser)
                .build();
        organizationRepository.save(testOrg);
    }

    @Test
    @DisplayName("Should get or create Organization policy with default settings")
    void shouldGetOrCreateOrganizationPolicy() {
        WorkspacePolicyEntity policy = workspacePolicyService.getOrCreateOrganizationPolicy(testOrg);

        assertThat(policy).isNotNull();
        assertThat(policy.getWorkspaceType()).isEqualTo(WorkspaceType.ORGANIZATION);
        assertThat(policy.getOrganization().getOrgCode()).isEqualTo(testOrg.getOrgCode());
        assertThat(policy.getExternalSharingMode()).isEqualTo(ExternalSharingMode.OFF);
        assertThat(policy.getDiscoverabilityMode()).isEqualTo(DiscoverabilityMode.DISCOVERABLE);

        // Subsequent call should retrieve existing policy
        WorkspacePolicyEntity secondCall = workspacePolicyService.getOrCreateOrganizationPolicy(testOrg);
        assertThat(secondCall.getId()).isEqualTo(policy.getId());
    }

    @Test
    @DisplayName("Should get or create Personal policy with default settings")
    void shouldGetOrCreatePersonalPolicy() {
        WorkspacePolicyEntity policy = workspacePolicyService.getOrCreatePersonalPolicy(testUser);

        assertThat(policy).isNotNull();
        assertThat(policy.getWorkspaceType()).isEqualTo(WorkspaceType.PERSONAL);
        assertThat(policy.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(policy.getExternalSharingMode()).isEqualTo(ExternalSharingMode.OFF);
        assertThat(policy.getDiscoverabilityMode()).isEqualTo(DiscoverabilityMode.PRIVATE);

        // Subsequent call should retrieve existing policy
        WorkspacePolicyEntity secondCall = workspacePolicyService.getOrCreatePersonalPolicy(testUser);
        assertThat(secondCall.getId()).isEqualTo(policy.getId());
    }

    @Test
    @DisplayName("Should update Organization policy settings")
    void shouldUpdateOrganizationPolicy() {
        WorkspacePolicyEntity updated = workspacePolicyService.updateOrganizationPolicy(
                testOrg.getOrgCode(),
                ExternalSharingMode.ON,
                DiscoverabilityMode.TRUSTED_ONLY
        );

        assertThat(updated.getExternalSharingMode()).isEqualTo(ExternalSharingMode.ON);
        assertThat(updated.getDiscoverabilityMode()).isEqualTo(DiscoverabilityMode.TRUSTED_ONLY);
    }

    @Test
    @DisplayName("Should update Personal policy settings")
    void shouldUpdatePersonalPolicy() {
        WorkspacePolicyEntity updated = workspacePolicyService.updatePersonalPolicy(
                testUser.getId(),
                ExternalSharingMode.ON,
                DiscoverabilityMode.DISCOVERABLE
        );

        assertThat(updated.getExternalSharingMode()).isEqualTo(ExternalSharingMode.ON);
        assertThat(updated.getDiscoverabilityMode()).isEqualTo(DiscoverabilityMode.DISCOVERABLE);
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent organization")
    void shouldThrowWhenUpdatingNonExistentOrg() {
        assertThrows(IllegalArgumentException.class, () -> {
            workspacePolicyService.updateOrganizationPolicy("NON_EXISTENT_ORG", ExternalSharingMode.ON, DiscoverabilityMode.PRIVATE);
        });
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent user")
    void shouldThrowWhenUpdatingNonExistentUser() {
        assertThrows(IllegalArgumentException.class, () -> {
            workspacePolicyService.updatePersonalPolicy("NON_EXISTENT_USER_ID", ExternalSharingMode.ON, DiscoverabilityMode.PRIVATE);
        });
    }

    @Test
    @DisplayName("Should update Organization policy with access request flags")
    void shouldUpdateOrganizationPolicyWithAccessRequestFlags() {
        WorkspacePolicyEntity updated = workspacePolicyService.updateOrganizationPolicy(
                testOrg.getOrgCode(),
                ExternalSharingMode.ON,
                DiscoverabilityMode.DISCOVERABLE,
                false,
                true,
                false
        );

        assertThat(updated.getAllowAccessRequests()).isFalse();
        assertThat(updated.getRequireRequestReason()).isTrue();
        assertThat(updated.getAllowExternalAccessRequests()).isFalse();
    }

    @Test
    @DisplayName("Should compute conservative effective policy for user with organization")
    void shouldComputeEffectivePolicyForUserWithOrganization() {
        // Update Org policy to require request reason
        workspacePolicyService.updateOrganizationPolicy(
                testOrg.getOrgCode(),
                ExternalSharingMode.ON,
                DiscoverabilityMode.DISCOVERABLE,
                true,
                true,
                true
        );

        testUser.setOrganization(testOrg);
        userRepository.save(testUser);

        WorkspacePolicyEntity effective = workspacePolicyService.getEffectivePolicyForUser(testUser);

        assertThat(effective).isNotNull();
        assertThat(effective.getRequireRequestReason()).isTrue();
    }

    @Test
    @DisplayName("Should return transient default Organization policy when invoked in read-only transaction")
    void shouldReturnTransientOrgPolicyInReadOnlyTransaction() {
        OrganizationEntity roOrg = OrganizationEntity.builder()
                .orgCode("ORG-RO-" + UUID.randomUUID().toString().substring(0, 8))
                .name("RO Organization")
                .createdBy(testUser)
                .build();
        organizationRepository.save(roOrg);

        org.springframework.transaction.support.TransactionTemplate roTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        roTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        roTemplate.setReadOnly(true);

        WorkspacePolicyEntity policy = roTemplate.execute(status ->
                workspacePolicyService.getOrCreateOrganizationPolicy(roOrg)
        );

        assertThat(policy).isNotNull();
        assertThat(policy.getId()).isNull();
        assertThat(workspacePolicyRepository.findByOrganization(roOrg)).isEmpty();
    }

    @Test
    @DisplayName("Should return transient default Personal policy when invoked in read-only transaction")
    void shouldReturnTransientPersonalPolicyInReadOnlyTransaction() {
        UserEntity roUser = UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("ro-user-" + UUID.randomUUID() + "@test.com")
                .firstName("RO")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();
        userRepository.save(roUser);

        org.springframework.transaction.support.TransactionTemplate roTemplate =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        roTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        roTemplate.setReadOnly(true);

        WorkspacePolicyEntity policy = roTemplate.execute(status ->
                workspacePolicyService.getOrCreatePersonalPolicy(roUser)
        );

        assertThat(policy).isNotNull();
        assertThat(policy.getId()).isNull();
        assertThat(workspacePolicyRepository.findByUser(roUser)).isEmpty();
    }
}
