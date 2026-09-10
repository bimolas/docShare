package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.entities.*;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.utils.PermissionHashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class TeamAllocationMigrationService implements ApplicationRunner {

    private final PermissionRepository permissionRepository;
    private final UserGroupRepository userGroupRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateLegacyTeamAllocations();
    }

    @Transactional
    public int migrateLegacyTeamAllocations() {
        // Query active legacy team allocations (recipientType=GROUP, permissionType=ALLOW, folder!=null)
        List<PermissionEntity> legacyAllocations = permissionRepository.findAll().stream()
                .filter(p -> !p.isRevoked() && !p.isSuspended())
                .filter(p -> p.getRecipientType() == RecipientType.GROUP)
                .filter(p -> p.getPermissionType() == PermissionType.ALLOW)
                .filter(p -> p.getTargetGroup() != null && p.getFolder() != null)
                .toList();

        if (legacyAllocations.isEmpty()) {
            log.info("No legacy team allocations found requiring migration.");
            return 0;
        }

        log.info("Found {} legacy team allocations to migrate to BOUNDARY + member delegations.", legacyAllocations.size());
        int migratedCount = 0;

        for (PermissionEntity alloc : legacyAllocations) {
            GroupEntity team = alloc.getTargetGroup();
            if (team == null || team.getStatus() == GroupStatus.ARCHIVED) {
                continue;
            }

            // 1. Promote allocation to PermissionType.BOUNDARY
            alloc.setPermissionType(PermissionType.BOUNDARY);
            String boundaryHash = PermissionHashUtils.calculateRecordHash(alloc);
            alloc.setRecordHash(boundaryHash);
            alloc.setBlockchainStatus(BlockchainStatus.PENDING);
            permissionRepository.save(alloc);

            String orgCode = team.getOrganization() != null ? team.getOrganization().getOrgCode() : alloc.getTargetOrgCode();

            if (auditLogService != null) {
                AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                        .action("MIGRATION_TEAM_ALLOCATION_TO_BOUNDARY")
                        .targetType("FOLDER")
                        .targetId(alloc.getFolder().getId())
                        .folderId(alloc.getFolder().getId())
                        .workspaceId(alloc.getWorkspaceId())
                        .groupId(team.getId())
                        .spaceType(SpaceType.ORGANIZATION)
                        .orgCode(orgCode)
                        .actorUserId("SYSTEM_MIGRATION")
                        .grantedBy(alloc.getGrantedBy())
                        .permissionLevel(alloc.getPermissionLevel())
                        .recordHash(boundaryHash)
                        .blockchainStatus(BlockchainStatus.PENDING)
                        .timestamp(Instant.now())
                        .details("Automated migration: promoted legacy GROUP ALLOW allocation to BOUNDARY")
                        .build();
                auditLogService.logPermissionChange(auditCmd);
            }

            // 2. Generate Team Member Access Delegations for currently active team members
            List<UserGroupEntity> userGroups = userGroupRepository.findByGroup_Id(team.getId());
            for (UserGroupEntity ug : userGroups) {
                if (ug.getUser() == null || ug.getUser().getEmail() == null) {
                    continue;
                }

                String memberEmail = ug.getUser().getEmail().trim();

                // Check if delegation already exists
                boolean exists = permissionRepository.findByTargetGroup_Id(team.getId()).stream()
                        .anyMatch(p -> !p.isRevoked()
                                && p.getRecipientType() == RecipientType.USER_ORGANIZATION_MEMBER
                                && memberEmail.equalsIgnoreCase(p.getTargetUserEmail())
                                && p.getFolder() != null && p.getFolder().getId().equals(alloc.getFolder().getId()));

                if (!exists) {
                    PermissionEntity delegation = PermissionEntity.builder()
                            .permissionType(PermissionType.ALLOW)
                            .permissionLevel(alloc.getPermissionLevel())
                            .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                            .targetGroup(team)
                            .targetUserEmail(memberEmail)
                            .targetOrgCode(orgCode)
                            .folder(alloc.getFolder())
                            .workspaceId(alloc.getWorkspaceId())
                            .grantorAuthorityTier(AuthorityTier.TEAM_MANAGER)
                            .grantedBy(alloc.getGrantedBy()) // Preserves historical truth!
                            .validFrom(alloc.getValidFrom() != null ? alloc.getValidFrom() : Instant.now())
                            .note("MIGRATED_DELEGATION: source_alloc_id=" + alloc.getId() + ", original_grantor=" + (alloc.getGrantedBy() != null ? alloc.getGrantedBy().getId() : "UNKNOWN") + ", migrated_at=" + Instant.now())
                            .build();

                    String delegHash = PermissionHashUtils.calculateRecordHash(delegation);
                    delegation.setRecordHash(delegHash);
                    delegation.setBlockchainStatus(BlockchainStatus.PENDING);
                    permissionRepository.save(delegation);

                    if (auditLogService != null) {
                        AuditPermissionChangeCommand delegAudit = AuditPermissionChangeCommand.builder()
                                .action("MIGRATION_GENERATE_TEAM_DELEGATION")
                                .targetType("FOLDER")
                                .targetId(alloc.getFolder().getId())
                                .folderId(alloc.getFolder().getId())
                                .workspaceId(alloc.getWorkspaceId())
                                .groupId(team.getId())
                                .spaceType(SpaceType.ORGANIZATION)
                                .orgCode(orgCode)
                                .actorUserId("SYSTEM_MIGRATION")
                                .grantedBy(alloc.getGrantedBy())
                                .grantedToEmail(memberEmail)
                                .permissionLevel(delegation.getPermissionLevel())
                                .recordHash(delegHash)
                                .blockchainStatus(BlockchainStatus.PENDING)
                                .timestamp(Instant.now())
                                .reason("AUTOMATED_MIGRATION_FROM_LEGACY_GROUP_ALLOCATION")
                                .details("Auto-migrated member delegation for active team member " + memberEmail + " from legacy group allocation ID " + alloc.getId())
                                .build();
                        auditLogService.logPermissionChange(delegAudit);
                    }
                }
            }
            migratedCount++;
        }

        log.info("Successfully migrated {} legacy team allocations to BOUNDARY.", migratedCount);
        return migratedCount;
    }
}
