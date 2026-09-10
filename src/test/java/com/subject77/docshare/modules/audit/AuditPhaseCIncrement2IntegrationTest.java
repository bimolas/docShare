package com.subject77.docshare.modules.audit;

import com.subject77.docshare.modules.audit.dto.UnifiedAuditEventResponseDto;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditQueryCriteria;
import com.subject77.docshare.modules.audit.entities.AuditEventType;
import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.enums.AuditScopeType;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.audit.services.UnifiedAuditService;
import com.subject77.docshare.modules.folder.dto.FolderRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.folder.services.FolderService;
import com.subject77.docshare.modules.organization.dto.GrantMemberAccessRequestDto;
import com.subject77.docshare.modules.organization.dto.GroupRequestDto;
import com.subject77.docshare.modules.organization.dto.GroupResponseDto;
import com.subject77.docshare.modules.organization.dto.TeamMemberAccessResponseDto;
import com.subject77.docshare.modules.organization.dto.UpdateMemberAccessRequestDto;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.organization.services.GroupService;
import com.subject77.docshare.modules.organization.services.TeamDelegationService;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.dto.CreateProjectWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.dto.ProjectWorkspaceResponseDto;
import com.subject77.docshare.modules.workspace.dto.WorkspaceAssignmentResponseDto;
import com.subject77.docshare.modules.workspace.dto.WorkspaceTeamAssignmentRequestDto;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class AuditPhaseCIncrement2IntegrationTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private TeamDelegationService teamDelegationService;

    @Autowired
    private ProjectWorkspaceService projectWorkspaceService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private FolderService folderService;

    @Autowired
    private UnifiedAuditService unifiedAuditService;

    @Autowired
    private PermissionAuditLogRepository permissionAuditLogRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @MockitoBean
    private PermissionEventPublisher permissionEventPublisher;

    private UserEntity orgAdmin;
    private UserEntity managerUser;
    private UserEntity memberUserA;
    private UserEntity memberUserB;
    private OrganizationEntity org;
    private ProjectWorkspaceResponseDto workspace;
    private FolderEntity rootFolder;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        orgAdmin = userRepository.save(UserEntity.builder()
                .id("admin-" + uid)
                .email("admin-" + uid + "@test.com")
                .firstName("Org")
                .lastName("Admin")
                .role(Role.ROLE_ORG_ADMIN)
                .build());

        managerUser = userRepository.save(UserEntity.builder()
                .id("mgr-" + uid)
                .email("mgr-" + uid + "@test.com")
                .firstName("Team")
                .lastName("Manager")
                .role(Role.ROLE_USER)
                .build());

        memberUserA = userRepository.save(UserEntity.builder()
                .id("membera-" + uid)
                .email("membera-" + uid + "@test.com")
                .firstName("Member")
                .lastName("Alpha")
                .role(Role.ROLE_USER)
                .build());

        memberUserB = userRepository.save(UserEntity.builder()
                .id("memberb-" + uid)
                .email("memberb-" + uid + "@test.com")
                .firstName("Member")
                .lastName("Beta")
                .role(Role.ROLE_USER)
                .build());

        org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORG_" + uid.toUpperCase())
                .name("Org " + uid)
                .createdBy(orgAdmin)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(orgAdmin)
                .organization(org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(managerUser)
                .organization(org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(memberUserA)
                .organization(org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(memberUserB)
                .organization(org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        // Create workspace for Org
        workspace = projectWorkspaceService.createWorkspace(
                CreateProjectWorkspaceRequestDto.builder()
                        .orgCode(org.getOrgCode())
                        .name("Workspace " + uid)
                        .description("Test workspace")
                        .build(),
                orgAdmin
        );

        rootFolder = folderRepository.findById(workspace.getRootFolderId()).orElseThrow();
    }

    @Test
    @DisplayName("C2.1: Full Team Governance Lifecycle emits canonical audit events under TEAM scope")
    void testTeamGovernanceLifecycleAudit() {
        // 1. Create Team with managerUser and initial member memberUserA
        GroupRequestDto createReq = GroupRequestDto.builder()
                .name("Backend Engineers")
                .description("Backend Team")
                .initialManagerUserId(managerUser.getId())
                .initialMemberUserIds(List.of(memberUserA.getId()))
                .build();

        GroupResponseDto group = groupService.createGroup(org.getOrgCode(), createReq, orgAdmin);
        assertThat(group).isNotNull();
        Long teamId = group.getId();

        // Verify TEAM_CREATE and initial TEAM_MEMBER_ADD in raw permission_audit_logs
        List<PermissionAuditLogEntity> rawLogs = permissionAuditLogRepository.findByGroupIdOrderByTimestampDesc(teamId);
        assertThat(rawLogs).isNotEmpty();

        boolean hasTeamCreate = rawLogs.stream().anyMatch(l -> "TEAM_CREATE".equals(l.getAction())
                && teamId.equals(l.getGroupId())
                && orgAdmin.getId().equals(l.getActorUserId())
                && "Backend Engineers".equals(l.getNewValue()));
        assertThat(hasTeamCreate).isTrue();

        boolean hasInitialMemberAdd = rawLogs.stream().anyMatch(l -> "TEAM_MEMBER_ADD".equals(l.getAction())
                && teamId.equals(l.getGroupId())
                && memberUserA.getEmail().equals(l.getGrantedToEmail()));
        assertThat(hasInitialMemberAdd).isTrue();

        // 2. Rename Team
        GroupRequestDto updateReq = GroupRequestDto.builder()
                .name("Core Platform Engineers")
                .description("Updated Backend Team")
                .build();
        groupService.updateGroup(org.getOrgCode(), teamId, updateReq, orgAdmin);

        // 3. Add Member B
        groupService.addMemberToGroup(org.getOrgCode(), teamId, memberUserB.getId(), orgAdmin);

        // 4. Transfer Team Manager to Member A
        groupService.transferTeamManager(org.getOrgCode(), teamId, memberUserA.getId(), orgAdmin);

        // 5. Remove Member B
        groupService.removeMemberFromGroup(org.getOrgCode(), teamId, memberUserB.getId(), orgAdmin);

        // 6. Archive Team
        groupService.archiveGroup(org.getOrgCode(), teamId, orgAdmin);

        // 7. Restore Team
        groupService.unarchiveGroup(org.getOrgCode(), teamId, orgAdmin);

        // 8. Query Unified Audit with scopeType=TEAM
        UnifiedAuditQueryCriteria teamCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.TEAM)
                .teamId(teamId)
                .build();

        Page<UnifiedAuditEventResponseDto> teamEvents = unifiedAuditService.queryEvents(
                teamCriteria, PageRequest.of(0, 50), orgAdmin);

        assertThat(teamEvents.getContent()).isNotEmpty();
        List<String> actions = teamEvents.getContent().stream()
                .map(UnifiedAuditEventResponseDto::getAction)
                .toList();

        assertThat(actions).contains(
                AuditEventType.TEAM_CREATE.name(),
                AuditEventType.TEAM_MEMBER_ADD.name(),
                AuditEventType.TEAM_RENAME.name(),
                AuditEventType.TEAM_MANAGER_TRANSFER.name(),
                AuditEventType.TEAM_MEMBER_REMOVE.name(),
                AuditEventType.TEAM_ARCHIVE.name(),
                AuditEventType.TEAM_RESTORE.name()
        );

        // Verify specific event payloads
        UnifiedAuditEventResponseDto renameEvent = teamEvents.getContent().stream()
                .filter(e -> AuditEventType.TEAM_RENAME.name().equals(e.getAction()))
                .findFirst()
                .orElseThrow();
        assertThat(renameEvent.getChanges().getPreviousValue()).isEqualTo("Backend Engineers");
        assertThat(renameEvent.getChanges().getNewValue()).isEqualTo("Core Platform Engineers");
        assertThat(renameEvent.getActor().getUserId()).isEqualTo(orgAdmin.getId());

        UnifiedAuditEventResponseDto transferEvent = teamEvents.getContent().stream()
                .filter(e -> AuditEventType.TEAM_MANAGER_TRANSFER.name().equals(e.getAction()))
                .findFirst()
                .orElseThrow();
        assertThat(transferEvent.getChanges().getPreviousValue()).isEqualTo(managerUser.getEmail());
        assertThat(transferEvent.getChanges().getNewValue()).isEqualTo(memberUserA.getEmail());
    }

    @Test
    @DisplayName("C2.2: Team Workspace Allocation appears under both TEAM and WORKSPACE scopes with TEAM_ALLOCATE_WORKSPACE")
    void testTeamWorkspaceAllocationDualScopeAudit() {
        GroupRequestDto createReq = GroupRequestDto.builder()
                .name("Frontend Team")
                .initialManagerUserId(managerUser.getId())
                .build();
        GroupResponseDto group = groupService.createGroup(org.getOrgCode(), createReq, orgAdmin);
        Long teamId = group.getId();

        FolderRequestDto subfolderReq = FolderRequestDto.builder()
                .name("UI Components")
                .parentFolderId(rootFolder.getId())
                .build();
        FolderResponseDto subfolder = folderService.createFolder(org.getOrgCode(), subfolderReq, orgAdmin);

        // Assign Team to Workspace Subfolder
        WorkspaceTeamAssignmentRequestDto assignReq = WorkspaceTeamAssignmentRequestDto.builder()
                .teamId(teamId)
                .folderId(subfolder.getId())
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        WorkspaceAssignmentResponseDto assignment = projectWorkspaceService.assignTeamToWorkspace(
                workspace.getId(), assignReq, orgAdmin);
        assertThat(assignment).isNotNull();

        // 1. Query Unified Audit with scopeType=TEAM
        UnifiedAuditQueryCriteria teamCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.TEAM)
                .teamId(teamId)
                .build();

        Page<UnifiedAuditEventResponseDto> teamEvents = unifiedAuditService.queryEvents(
                teamCriteria, PageRequest.of(0, 20), orgAdmin);

        UnifiedAuditEventResponseDto teamAllocEvent = teamEvents.getContent().stream()
                .filter(e -> AuditEventType.TEAM_ALLOCATE_WORKSPACE.name().equals(e.getAction()))
                .findFirst()
                .orElse(null);

        assertThat(teamAllocEvent).isNotNull();
        assertThat(teamAllocEvent.getActor().getUserId()).isEqualTo(orgAdmin.getId());
        assertThat(teamAllocEvent.getTarget().getType()).isEqualTo("FOLDER");
        assertThat(teamAllocEvent.getTarget().getId()).isEqualTo(subfolder.getId());
        assertThat(teamAllocEvent.getChanges().getNewValue()).isEqualTo("EDIT");
        assertThat(teamAllocEvent.getLocation().getWorkspaceId()).isEqualTo(workspace.getId());

        // 2. Query Unified Audit with scopeType=WORKSPACE
        UnifiedAuditQueryCriteria wsCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.WORKSPACE)
                .workspaceId(workspace.getId())
                .build();

        Page<UnifiedAuditEventResponseDto> wsEvents = unifiedAuditService.queryEvents(
                wsCriteria, PageRequest.of(0, 20), orgAdmin);

        boolean foundInWs = wsEvents.getContent().stream()
                .anyMatch(e -> AuditEventType.TEAM_ALLOCATE_WORKSPACE.name().equals(e.getAction())
                        && e.getTarget().getId().equals(subfolder.getId()));
        assertThat(foundInWs).isTrue();
    }

    @Test
    @DisplayName("C2.3: Team Member Delegation lifecycle logs TEAM_DELEGATION_GRANT, UPDATE, and REVOKE under TEAM scope")
    void testTeamMemberDelegationLifecycleAudit() {
        GroupRequestDto createReq = GroupRequestDto.builder()
                .name("Security Analysts")
                .initialManagerUserId(managerUser.getId())
                .initialMemberUserIds(List.of(memberUserA.getId()))
                .build();
        GroupResponseDto group = groupService.createGroup(org.getOrgCode(), createReq, orgAdmin);
        Long teamId = group.getId();

        // Allocate workspace boundary to team
        WorkspaceTeamAssignmentRequestDto assignReq = WorkspaceTeamAssignmentRequestDto.builder()
                .teamId(teamId)
                .folderId(rootFolder.getId())
                .permissionLevel(PermissionLevel.EDIT)
                .build();
        projectWorkspaceService.assignTeamToWorkspace(workspace.getId(), assignReq, orgAdmin);

        // 1. Grant member access delegation by Team Manager
        GrantMemberAccessRequestDto grantReq = GrantMemberAccessRequestDto.builder()
                .userEmail(memberUserA.getEmail())
                .folderId(rootFolder.getId())
                .permissionLevel(PermissionLevel.VIEW)
                .note("Granting VIEW on root folder")
                .build();

        TeamMemberAccessResponseDto delegation = teamDelegationService.grantMemberAccess(
                org.getOrgCode(), teamId, grantReq, managerUser);
        assertThat(delegation).isNotNull();

        // 2. Update member access delegation
        UpdateMemberAccessRequestDto updateReq = UpdateMemberAccessRequestDto.builder()
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .build();

        teamDelegationService.updateMemberAccess(
                org.getOrgCode(), teamId, delegation.getPermissionId(), updateReq, managerUser);

        // 3. Revoke member access delegation
        teamDelegationService.revokeMemberAccess(
                org.getOrgCode(), teamId, delegation.getPermissionId(), managerUser);

        // 4. Query Unified Audit with scopeType=TEAM
        UnifiedAuditQueryCriteria teamCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.TEAM)
                .teamId(teamId)
                .build();

        Page<UnifiedAuditEventResponseDto> teamEvents = unifiedAuditService.queryEvents(
                teamCriteria, PageRequest.of(0, 50), orgAdmin);

        List<String> actions = teamEvents.getContent().stream()
                .map(UnifiedAuditEventResponseDto::getAction)
                .toList();

        assertThat(actions).contains(
                AuditEventType.TEAM_DELEGATION_GRANT.name(),
                AuditEventType.TEAM_DELEGATION_UPDATE.name(),
                AuditEventType.TEAM_DELEGATION_REVOKE.name()
        );

        UnifiedAuditEventResponseDto grantEvent = teamEvents.getContent().stream()
                .filter(e -> AuditEventType.TEAM_DELEGATION_GRANT.name().equals(e.getAction()))
                .findFirst()
                .orElseThrow();
        assertThat(grantEvent.getActor().getUserId()).isEqualTo(managerUser.getId());
        assertThat(grantEvent.getChanges().getNewValue()).isEqualTo("VIEW");

        UnifiedAuditEventResponseDto updateEvent = teamEvents.getContent().stream()
                .filter(e -> AuditEventType.TEAM_DELEGATION_UPDATE.name().equals(e.getAction()))
                .findFirst()
                .orElseThrow();
        assertThat(updateEvent.getChanges().getPreviousValue()).isEqualTo("VIEW");
        assertThat(updateEvent.getChanges().getNewValue()).isEqualTo("DOWNLOAD");

        UnifiedAuditEventResponseDto revokeEvent = teamEvents.getContent().stream()
                .filter(e -> AuditEventType.TEAM_DELEGATION_REVOKE.name().equals(e.getAction()))
                .findFirst()
                .orElseThrow();
        assertThat(revokeEvent.getChanges().getPreviousValue()).isEqualTo("DOWNLOAD");
    }

    @Test
    @DisplayName("C2.4: Member removal from team cascade-revokes delegations with MEMBER_REMOVED_FROM_TEAM reason")
    void testMemberRemovalCascadeDelegationRevokeAudit() {
        GroupRequestDto createReq = GroupRequestDto.builder()
                .name("Dev Team")
                .initialManagerUserId(managerUser.getId())
                .initialMemberUserIds(List.of(memberUserA.getId()))
                .build();
        GroupResponseDto group = groupService.createGroup(org.getOrgCode(), createReq, orgAdmin);
        Long teamId = group.getId();

        // Boundary allocation
        WorkspaceTeamAssignmentRequestDto assignReq = WorkspaceTeamAssignmentRequestDto.builder()
                .teamId(teamId)
                .folderId(rootFolder.getId())
                .permissionLevel(PermissionLevel.EDIT)
                .build();
        projectWorkspaceService.assignTeamToWorkspace(workspace.getId(), assignReq, orgAdmin);

        // Delegation to Member A
        GrantMemberAccessRequestDto grantReq = GrantMemberAccessRequestDto.builder()
                .userEmail(memberUserA.getEmail())
                .folderId(rootFolder.getId())
                .permissionLevel(PermissionLevel.VIEW)
                .build();
        teamDelegationService.grantMemberAccess(org.getOrgCode(), teamId, grantReq, managerUser);

        // Remove Member A from group
        groupService.removeMemberFromGroup(org.getOrgCode(), teamId, memberUserA.getId(), orgAdmin);

        // Verify TEAM_DELEGATION_REVOKE emitted with reason MEMBER_REMOVED_FROM_TEAM
        List<PermissionAuditLogEntity> rawLogs = permissionAuditLogRepository.findByGroupIdOrderByTimestampDesc(teamId);
        PermissionAuditLogEntity cascadeLog = rawLogs.stream()
                .filter(l -> "TEAM_DELEGATION_REVOKE".equals(l.getAction())
                        && "MEMBER_REMOVED_FROM_TEAM".equals(l.getReason()))
                .findFirst()
                .orElse(null);

        assertThat(cascadeLog).isNotNull();
        assertThat(cascadeLog.getActorUserId()).isEqualTo(orgAdmin.getId());
        assertThat(cascadeLog.getGrantedToEmail()).isEqualTo(memberUserA.getEmail());

        // Query unified audit under TEAM scope
        UnifiedAuditQueryCriteria teamCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.TEAM)
                .teamId(teamId)
                .build();

        Page<UnifiedAuditEventResponseDto> teamEvents = unifiedAuditService.queryEvents(
                teamCriteria, PageRequest.of(0, 50), orgAdmin);

        boolean foundCascadeRevoke = teamEvents.getContent().stream()
                .anyMatch(e -> AuditEventType.TEAM_DELEGATION_REVOKE.name().equals(e.getAction())
                        && e.getChanges() != null
                        && "MEMBER_REMOVED_FROM_TEAM".equals(e.getChanges().getReason()));
        assertThat(foundCascadeRevoke).isTrue();
    }

    @Test
    @DisplayName("C2.5: Boundary narrowing and ceiling clamping emit TEAM_DELEGATION_REVOKE and TEAM_DELEGATION_UPDATE")
    void testBoundaryNarrowingAndCeilingClampingAudit() {
        GroupRequestDto createReq = GroupRequestDto.builder()
                .name("Operations Team")
                .initialManagerUserId(managerUser.getId())
                .initialMemberUserIds(List.of(memberUserA.getId(), memberUserB.getId()))
                .build();
        GroupResponseDto group = groupService.createGroup(org.getOrgCode(), createReq, orgAdmin);
        Long teamId = group.getId();

        // Create two subfolders
        FolderRequestDto sub1Req = FolderRequestDto.builder()
                .name("Area 1")
                .parentFolderId(rootFolder.getId())
                .build();
        FolderResponseDto subfolder1 = folderService.createFolder(org.getOrgCode(), sub1Req, orgAdmin);

        FolderRequestDto sub2Req = FolderRequestDto.builder()
                .name("Area 2")
                .parentFolderId(rootFolder.getId())
                .build();
        FolderResponseDto subfolder2 = folderService.createFolder(org.getOrgCode(), sub2Req, orgAdmin);

        // Initial boundary allocation on rootFolder with EDIT ceiling
        WorkspaceTeamAssignmentRequestDto assignReq = WorkspaceTeamAssignmentRequestDto.builder()
                .teamId(teamId)
                .folderId(rootFolder.getId())
                .permissionLevel(PermissionLevel.EDIT)
                .build();
        projectWorkspaceService.assignTeamToWorkspace(workspace.getId(), assignReq, orgAdmin);

        // Delegation 1: Member A on Area 1 with EDIT
        teamDelegationService.grantMemberAccess(org.getOrgCode(), teamId,
                GrantMemberAccessRequestDto.builder()
                        .userEmail(memberUserA.getEmail())
                        .folderId(subfolder1.getId())
                        .permissionLevel(PermissionLevel.EDIT)
                        .build(),
                managerUser);

        // Delegation 2: Member B on Area 2 with EDIT
        teamDelegationService.grantMemberAccess(org.getOrgCode(), teamId,
                GrantMemberAccessRequestDto.builder()
                        .userEmail(memberUserB.getEmail())
                        .folderId(subfolder2.getId())
                        .permissionLevel(PermissionLevel.EDIT)
                        .build(),
                managerUser);

        // Now narrow boundary to Area 1 only AND reduce ceiling to VIEW!
        // This should:
        // 1. Cascade revoke Member B's delegation on Area 2 (out of boundary -> REVOKED_DUE_TO_BOUNDARY_CHANGE)
        // 2. Ceiling clamp Member A's delegation on Area 1 from EDIT to VIEW (CEILING_CLAMPED)
        WorkspaceTeamAssignmentRequestDto narrowReq = WorkspaceTeamAssignmentRequestDto.builder()
                .teamId(teamId)
                .folderId(subfolder1.getId())
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        projectWorkspaceService.assignTeamToWorkspace(workspace.getId(), narrowReq, orgAdmin);

        // Query raw audit logs
        List<PermissionAuditLogEntity> logs = permissionAuditLogRepository.findByGroupIdOrderByTimestampDesc(teamId);

        boolean hasOutOfBoundaryRevoke = logs.stream().anyMatch(l -> "TEAM_DELEGATION_REVOKE".equals(l.getAction())
                && "REVOKED_DUE_TO_BOUNDARY_CHANGE".equals(l.getReason())
                && memberUserB.getEmail().equals(l.getGrantedToEmail()));
        assertThat(hasOutOfBoundaryRevoke).isTrue();

        boolean hasCeilingClamp = logs.stream().anyMatch(l -> "TEAM_DELEGATION_UPDATE".equals(l.getAction())
                && "CEILING_CLAMPED".equals(l.getReason())
                && "EDIT".equals(l.getPreviousValue())
                && "VIEW".equals(l.getNewValue())
                && memberUserA.getEmail().equals(l.getGrantedToEmail()));
        assertThat(hasCeilingClamp).isTrue();
    }

    @Test
    @DisplayName("C2.6: Workspace deallocation emits TEAM_DEALLOCATE and cascade REVOKED_DUE_TO_BOUNDARY_REVOCATION")
    void testWorkspaceDeallocationAudit() {
        GroupRequestDto createReq = GroupRequestDto.builder()
                .name("Deallocation Test Team")
                .initialManagerUserId(managerUser.getId())
                .initialMemberUserIds(List.of(memberUserA.getId()))
                .build();
        GroupResponseDto group = groupService.createGroup(org.getOrgCode(), createReq, orgAdmin);
        Long teamId = group.getId();

        // Assign team to workspace
        WorkspaceTeamAssignmentRequestDto assignReq = WorkspaceTeamAssignmentRequestDto.builder()
                .teamId(teamId)
                .folderId(rootFolder.getId())
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .build();
        WorkspaceAssignmentResponseDto assignment = projectWorkspaceService.assignTeamToWorkspace(
                workspace.getId(), assignReq, orgAdmin);

        // Grant member access delegation
        teamDelegationService.grantMemberAccess(org.getOrgCode(), teamId,
                GrantMemberAccessRequestDto.builder()
                        .userEmail(memberUserA.getEmail())
                        .folderId(rootFolder.getId())
                        .permissionLevel(PermissionLevel.DOWNLOAD)
                        .build(),
                managerUser);

        // Revoke workspace assignment
        projectWorkspaceService.revokeWorkspaceAssignment(
                workspace.getId(), assignment.getPermissionId(), orgAdmin);

        // Verify TEAM_DEALLOCATE in raw logs
        List<PermissionAuditLogEntity> logs = permissionAuditLogRepository.findByGroupIdOrderByTimestampDesc(teamId);

        boolean hasDeallocate = logs.stream().anyMatch(l -> "TEAM_DEALLOCATE".equals(l.getAction())
                && "DOWNLOAD".equals(l.getPreviousValue())
                && teamId.equals(l.getGroupId()));
        assertThat(hasDeallocate).isTrue();

        boolean hasCascadeRevoke = logs.stream().anyMatch(l -> "TEAM_DELEGATION_REVOKE".equals(l.getAction())
                && "BOUNDARY_REVOKED".equals(l.getReason())
                && memberUserA.getEmail().equals(l.getGrantedToEmail()));
        assertThat(hasCascadeRevoke).isTrue();

        // Query unified audit under TEAM scope
        UnifiedAuditQueryCriteria teamCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.TEAM)
                .teamId(teamId)
                .build();

        Page<UnifiedAuditEventResponseDto> teamEvents = unifiedAuditService.queryEvents(
                teamCriteria, PageRequest.of(0, 50), orgAdmin);

        boolean foundDeallocate = teamEvents.getContent().stream()
                .anyMatch(e -> AuditEventType.TEAM_DEALLOCATE.name().equals(e.getAction()));
        assertThat(foundDeallocate).isTrue();
    }
}
