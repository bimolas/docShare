package com.subject77.docshare.modules.policy.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.exceptions.GlobalExceptionHandler;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.policy.dto.UpdateWorkspacePolicyRequestDto;
import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.entities.WorkspaceType;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkspacePolicyControllerTest {

    private static final String TARGET_ORG = "ACME_CORP";
    private static final String OTHER_ORG = "OTHER_ORG";

    @Mock
    private WorkspacePolicyService workspacePolicyService;

    @Mock
    private PermissionEvaluatorService evaluatorService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private WorkspacePolicyController workspacePolicyController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UserEntity superAdmin;
    private UserEntity targetOrgAdmin;
    private UserEntity otherOrgAdmin;
    private UserEntity targetOrgManager;
    private UserEntity workspaceManager;
    private UserEntity teamManager;
    private UserEntity ordinaryMember;
    private UserEntity unrelatedUser;

    private WorkspacePolicyEntity sampleOrgPolicy;
    private WorkspacePolicyEntity samplePersonalPolicy;
    private UpdateWorkspacePolicyRequestDto updateRequest;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        lenient().when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(workspacePolicyController)
                .setCustomArgumentResolvers(resolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        OrganizationEntity orgAcme = OrganizationEntity.builder()
                .orgCode(TARGET_ORG)
                .name("Acme Corp")
                .build();

        OrganizationEntity orgOther = OrganizationEntity.builder()
                .orgCode(OTHER_ORG)
                .name("Other Org")
                .build();

        superAdmin = UserEntity.builder()
                .id("usr-super-admin")
                .email("superadmin@system.com")
                .firstName("Super")
                .lastName("Admin")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        targetOrgAdmin = UserEntity.builder()
                .id("usr-org-admin")
                .email("admin@acme.com")
                .firstName("Org")
                .lastName("Admin")
                .role(Role.ROLE_ORG_ADMIN)
                .organization(orgAcme)
                .build();

        otherOrgAdmin = UserEntity.builder()
                .id("usr-other-org-admin")
                .email("admin@other.com")
                .firstName("Other")
                .lastName("Admin")
                .role(Role.ROLE_ORG_ADMIN)
                .organization(orgOther)
                .build();

        targetOrgManager = UserEntity.builder()
                .id("usr-org-manager")
                .email("manager@acme.com")
                .firstName("Org")
                .lastName("Manager")
                .role(Role.ROLE_ORG_MANAGER)
                .organization(orgAcme)
                .build();

        workspaceManager = UserEntity.builder()
                .id("usr-workspace-manager")
                .email("wsmanager@acme.com")
                .firstName("Workspace")
                .lastName("Manager")
                .role(Role.ROLE_USER)
                .organization(orgAcme)
                .build();

        teamManager = UserEntity.builder()
                .id("usr-team-manager")
                .email("teammanager@acme.com")
                .firstName("Team")
                .lastName("Manager")
                .role(Role.ROLE_USER)
                .organization(orgAcme)
                .build();

        ordinaryMember = UserEntity.builder()
                .id("usr-ordinary-member")
                .email("member@acme.com")
                .firstName("Ordinary")
                .lastName("Member")
                .role(Role.ROLE_USER)
                .organization(orgAcme)
                .build();

        unrelatedUser = UserEntity.builder()
                .id("usr-unrelated")
                .email("unrelated@external.com")
                .firstName("Unrelated")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        sampleOrgPolicy = WorkspacePolicyEntity.builder()
                .id(1L)
                .workspaceType(WorkspaceType.ORGANIZATION)
                .organization(orgAcme)
                .externalSharingMode(ExternalSharingMode.ON)
                .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE)
                .allowAccessRequests(true)
                .requireRequestReason(true)
                .allowExternalAccessRequests(false)
                .build();

        samplePersonalPolicy = WorkspacePolicyEntity.builder()
                .id(2L)
                .workspaceType(WorkspaceType.PERSONAL)
                .user(ordinaryMember)
                .externalSharingMode(ExternalSharingMode.ON)
                .discoverabilityMode(DiscoverabilityMode.PRIVATE)
                .allowAccessRequests(false)
                .requireRequestReason(false)
                .allowExternalAccessRequests(false)
                .build();

        updateRequest = UpdateWorkspacePolicyRequestDto.builder()
                .externalSharingMode(ExternalSharingMode.ON)
                .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE)
                .allowAccessRequests(true)
                .requireRequestReason(true)
                .allowExternalAccessRequests(false)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser(UserEntity user) {
        Jwt jwt = new Jwt(
                "token-" + user.getId(),
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", user.getId(), "email", user.getEmail())
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        lenient().when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(user);
    }

    // -------------------------------------------------------------------------
    // 1. SUPER_ADMIN can update organization policy
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("1. SUPER_ADMIN can update organization policy")
    void updateOrgPolicy_SuperAdmin_Allowed() throws Exception {
        authenticateUser(superAdmin);
        when(evaluatorService.isUserAdminOfOrg(superAdmin, TARGET_ORG)).thenReturn(true);
        when(workspacePolicyService.updateOrganizationPolicy(
                eq(TARGET_ORG),
                eq(ExternalSharingMode.ON),
                eq(DiscoverabilityMode.DISCOVERABLE),
                eq(true),
                eq(true),
                eq(false),
                eq(superAdmin)))
                .thenReturn(sampleOrgPolicy);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/policies", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceType").value("ORGANIZATION"))
                .andExpect(jsonPath("$.externalSharingMode").value("ON"))
                .andExpect(jsonPath("$.discoverabilityMode").value("DISCOVERABLE"));

        verify(evaluatorService).isUserAdminOfOrg(superAdmin, TARGET_ORG);
        verify(workspacePolicyService).updateOrganizationPolicy(
                eq(TARGET_ORG), any(), any(), any(), any(), any(), eq(superAdmin));
    }

    // -------------------------------------------------------------------------
    // 2. ORG_ADMIN of target organization can update organization policy
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("2. ORG_ADMIN of target organization can update organization policy")
    void updateOrgPolicy_TargetOrgAdmin_Allowed() throws Exception {
        authenticateUser(targetOrgAdmin);
        when(evaluatorService.isUserAdminOfOrg(targetOrgAdmin, TARGET_ORG)).thenReturn(true);
        when(workspacePolicyService.updateOrganizationPolicy(
                eq(TARGET_ORG),
                eq(ExternalSharingMode.ON),
                eq(DiscoverabilityMode.DISCOVERABLE),
                eq(true),
                eq(true),
                eq(false),
                eq(targetOrgAdmin)))
                .thenReturn(sampleOrgPolicy);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/policies", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceType").value("ORGANIZATION"))
                .andExpect(jsonPath("$.externalSharingMode").value("ON"))
                .andExpect(jsonPath("$.discoverabilityMode").value("DISCOVERABLE"));

        verify(evaluatorService).isUserAdminOfOrg(targetOrgAdmin, TARGET_ORG);
        verify(workspacePolicyService).updateOrganizationPolicy(
                eq(TARGET_ORG), any(), any(), any(), any(), any(), eq(targetOrgAdmin));
    }

    // -------------------------------------------------------------------------
    // 3. ORG_ADMIN of another organization is denied
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("3. ORG_ADMIN of another organization is denied")
    void updateOrgPolicy_OtherOrgAdmin_Denied() throws Exception {
        authenticateUser(otherOrgAdmin);
        when(evaluatorService.isUserAdminOfOrg(otherOrgAdmin, TARGET_ORG)).thenReturn(false);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/policies", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Organization administrator privileges required to update organization workspace policy"));

        verify(evaluatorService).isUserAdminOfOrg(otherOrgAdmin, TARGET_ORG);
        verify(workspacePolicyService, never()).updateOrganizationPolicy(any(), any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 4. ORG_MANAGER of target organization is denied
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("4. ORG_MANAGER of target organization is denied")
    void updateOrgPolicy_OrgManager_Denied() throws Exception {
        authenticateUser(targetOrgManager);
        when(evaluatorService.isUserAdminOfOrg(targetOrgManager, TARGET_ORG)).thenReturn(false);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/policies", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Organization administrator privileges required to update organization workspace policy"));

        verify(evaluatorService).isUserAdminOfOrg(targetOrgManager, TARGET_ORG);
        verify(workspacePolicyService, never()).updateOrganizationPolicy(any(), any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 5. Workspace Manager is denied
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("5. Workspace Manager is denied")
    void updateOrgPolicy_WorkspaceManager_Denied() throws Exception {
        authenticateUser(workspaceManager);
        when(evaluatorService.isUserAdminOfOrg(workspaceManager, TARGET_ORG)).thenReturn(false);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/policies", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Organization administrator privileges required to update organization workspace policy"));

        verify(evaluatorService).isUserAdminOfOrg(workspaceManager, TARGET_ORG);
        verify(workspacePolicyService, never()).updateOrganizationPolicy(any(), any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 6. Team Manager is denied
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("6. Team Manager is denied")
    void updateOrgPolicy_TeamManager_Denied() throws Exception {
        authenticateUser(teamManager);
        when(evaluatorService.isUserAdminOfOrg(teamManager, TARGET_ORG)).thenReturn(false);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/policies", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Organization administrator privileges required to update organization workspace policy"));

        verify(evaluatorService).isUserAdminOfOrg(teamManager, TARGET_ORG);
        verify(workspacePolicyService, never()).updateOrganizationPolicy(any(), any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 7. Ordinary organization member is denied
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("7. Ordinary organization member is denied")
    void updateOrgPolicy_OrdinaryMember_Denied() throws Exception {
        authenticateUser(ordinaryMember);
        when(evaluatorService.isUserAdminOfOrg(ordinaryMember, TARGET_ORG)).thenReturn(false);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/policies", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Organization administrator privileges required to update organization workspace policy"));

        verify(evaluatorService).isUserAdminOfOrg(ordinaryMember, TARGET_ORG);
        verify(workspacePolicyService, never()).updateOrganizationPolicy(any(), any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 8. Unrelated authenticated user is denied
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("8. Unrelated authenticated user is denied")
    void updateOrgPolicy_UnrelatedUser_Denied() throws Exception {
        authenticateUser(unrelatedUser);
        when(evaluatorService.isUserAdminOfOrg(unrelatedUser, TARGET_ORG)).thenReturn(false);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/policies", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Organization administrator privileges required to update organization workspace policy"));

        verify(evaluatorService).isUserAdminOfOrg(unrelatedUser, TARGET_ORG);
        verify(workspacePolicyService, never()).updateOrganizationPolicy(any(), any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 9. Personal policy update preserves existing behavior
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("9. Personal policy update preserves existing behavior without org-admin check")
    void updatePersonalPolicy_PreservesExistingBehavior() throws Exception {
        authenticateUser(ordinaryMember);
        when(workspacePolicyService.updatePersonalPolicy(
                eq(ordinaryMember.getId()),
                eq(ExternalSharingMode.ON),
                eq(DiscoverabilityMode.DISCOVERABLE),
                eq(true),
                eq(true),
                eq(false),
                eq(ordinaryMember)))
                .thenReturn(samplePersonalPolicy);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/policies", "personal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceType").value("PERSONAL"))
                .andExpect(jsonPath("$.externalSharingMode").value("ON"))
                .andExpect(jsonPath("$.discoverabilityMode").value("PRIVATE"));

        // Must NOT call org admin check for personal workspace policy
        verify(evaluatorService, never()).isUserAdminOfOrg(any(), any());
        verify(workspacePolicyService).updatePersonalPolicy(
                eq(ordinaryMember.getId()), any(), any(), any(), any(), any(), eq(ordinaryMember));
        verify(workspacePolicyService, never()).updateOrganizationPolicy(any(), any(), any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // 10. Existing policy read behavior remains unchanged
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("10a. GET organization policy remains unchanged")
    void getOrgPolicy_RemainsUnchanged() throws Exception {
        authenticateUser(ordinaryMember);
        when(workspacePolicyService.getOrCreateOrganizationPolicy(TARGET_ORG))
                .thenReturn(sampleOrgPolicy);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/policies", TARGET_ORG)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceType").value("ORGANIZATION"))
                .andExpect(jsonPath("$.externalSharingMode").value("ON"));

        verify(workspacePolicyService).getOrCreateOrganizationPolicy(TARGET_ORG);
    }

    @Test
    @DisplayName("10b. GET personal policy remains unchanged")
    void getPersonalPolicy_RemainsUnchanged() throws Exception {
        authenticateUser(ordinaryMember);
        when(workspacePolicyService.getOrCreatePersonalPolicy(ordinaryMember))
                .thenReturn(samplePersonalPolicy);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/policies", "personal")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceType").value("PERSONAL"))
                .andExpect(jsonPath("$.externalSharingMode").value("ON"));

        verify(workspacePolicyService).getOrCreatePersonalPolicy(ordinaryMember);
    }

    // -------------------------------------------------------------------------
    // Bonus/Edge Case: Unauthenticated users are denied
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Unauthenticated user update organization policy is denied")
    void updateOrgPolicy_Unauthenticated_Denied() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/policies", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verify(workspacePolicyService, never()).updateOrganizationPolicy(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Unauthenticated user update personal policy is denied")
    void updatePersonalPolicy_Unauthenticated_Denied() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/policies", "personal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
