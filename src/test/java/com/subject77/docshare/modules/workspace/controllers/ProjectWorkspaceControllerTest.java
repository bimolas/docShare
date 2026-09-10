package com.subject77.docshare.modules.workspace.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.exceptions.GlobalExceptionHandler;
import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.services.UserService;
import com.subject77.docshare.modules.workspace.dto.*;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProjectWorkspaceControllerTest {

    private static final String TARGET_ORG = "ACME_CORP";
    private static final String OTHER_ORG = "OTHER_CORP";

    @Mock
    private ProjectWorkspaceService projectWorkspaceService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private ProjectWorkspaceController projectWorkspaceController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UserEntity orgAdmin;
    private UserEntity regularUser;
    private UserEntity otherOrgUser;

    private ProjectWorkspaceResponseDto sampleWorkspaceDto;
    private WorkspaceAssignmentResponseDto sampleTeamAssignmentDto;
    private WorkspaceAssignmentResponseDto sampleUserAssignmentDto;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        lenient().when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(projectWorkspaceController)
                .setCustomArgumentResolvers(resolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        OrganizationEntity acmeOrg = OrganizationEntity.builder()
                .orgCode(TARGET_ORG)
                .name("Acme Corp")
                .build();

        OrganizationEntity otherOrg = OrganizationEntity.builder()
                .orgCode(OTHER_ORG)
                .name("Other Corp")
                .build();

        orgAdmin = UserEntity.builder()
                .id("usr-org-admin")
                .email("admin@acme.com")
                .role(Role.ROLE_ORG_ADMIN)
                .organization(acmeOrg)
                .build();

        regularUser = UserEntity.builder()
                .id("usr-regular-user")
                .email("member@acme.com")
                .role(Role.ROLE_USER)
                .organization(acmeOrg)
                .build();

        otherOrgUser = UserEntity.builder()
                .id("usr-other-user")
                .email("user@other.com")
                .role(Role.ROLE_USER)
                .organization(otherOrg)
                .build();

        sampleWorkspaceDto = ProjectWorkspaceResponseDto.builder()
                .id(10L)
                .orgCode(TARGET_ORG)
                .name("Finance")
                .description("Finance documents")
                .rootFolderId(100L)
                .rootFolderName("Finance")
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdByUserId(orgAdmin.getId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        sampleTeamAssignmentDto = WorkspaceAssignmentResponseDto.builder()
                .permissionId(50L)
                .workspaceId(10L)
                .folderId(100L)
                .folderName("Finance")
                .teamId(5L)
                .teamName("Finance Team")
                .permissionLevel(PermissionLevel.VIEW)
                .permissionType(PermissionType.ALLOW)
                .grantedByUserId(orgAdmin.getId())
                .grantedByUserEmail(orgAdmin.getEmail())
                .grantedAt(Instant.now())
                .build();

        sampleUserAssignmentDto = WorkspaceAssignmentResponseDto.builder()
                .permissionId(51L)
                .workspaceId(10L)
                .folderId(100L)
                .folderName("Finance")
                .userEmail("member@acme.com")
                .permissionLevel(PermissionLevel.EDIT)
                .permissionType(PermissionType.ALLOW)
                .grantedByUserId(orgAdmin.getId())
                .grantedByUserEmail(orgAdmin.getEmail())
                .grantedAt(Instant.now())
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
    // 1. list workspaces for organization
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("1. List workspaces for organization returns 200 with workspace list")
    void listWorkspacesForOrganization_Success() throws Exception {
        authenticateUser(orgAdmin);
        when(projectWorkspaceService.getWorkspacesForOrganization(eq(TARGET_ORG), eq(orgAdmin)))
                .thenReturn(List.of(sampleWorkspaceDto));

        mockMvc.perform(get("/api/v1/organizations/{orgCode}/workspaces", TARGET_ORG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(10)))
                .andExpect(jsonPath("$[0].name", is("Finance")))
                .andExpect(jsonPath("$[0].orgCode", is(TARGET_ORG)))
                .andExpect(jsonPath("$[0].status", is("ACTIVE")));
    }

    // -------------------------------------------------------------------------
    // 2. create workspace
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("2a. Create workspace with matching orgCode returns 201")
    void createWorkspace_WithMatchingOrgCode_Success() throws Exception {
        authenticateUser(orgAdmin);
        CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                .orgCode(TARGET_ORG)
                .name("Finance")
                .description("Finance documents")
                .build();

        when(projectWorkspaceService.createWorkspace(any(CreateProjectWorkspaceRequestDto.class), eq(orgAdmin)))
                .thenReturn(sampleWorkspaceDto);

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/workspaces", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.name", is("Finance")))
                .andExpect(jsonPath("$.orgCode", is(TARGET_ORG)));
    }

    @Test
    @DisplayName("2b. Create workspace omitting orgCode in body adopts path orgCode and returns 201")
    void createWorkspace_WithoutOrgCodeInBody_AdoptsPathOrgCode() throws Exception {
        authenticateUser(orgAdmin);
        // JSON body has no orgCode field
        String jsonBody = "{\"name\":\"Finance\",\"description\":\"Finance documents\"}";

        when(projectWorkspaceService.createWorkspace(
                argThat(req -> TARGET_ORG.equals(req.getOrgCode()) && "Finance".equals(req.getName())),
                eq(orgAdmin)
        )).thenReturn(sampleWorkspaceDto);

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/workspaces", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.name", is("Finance")));
    }

    // -------------------------------------------------------------------------
    // 3. get workspace
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("3. Get workspace by ID returns 200")
    void getWorkspace_Success() throws Exception {
        authenticateUser(orgAdmin);
        when(projectWorkspaceService.getWorkspaceById(eq(10L), eq(orgAdmin)))
                .thenReturn(sampleWorkspaceDto);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(10)))
                .andExpect(jsonPath("$.name", is("Finance")))
                .andExpect(jsonPath("$.rootFolderId", is(100)));
    }

    // -------------------------------------------------------------------------
    // 4. update workspace
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("4. Update workspace returns 200 with updated fields")
    void updateWorkspace_Success() throws Exception {
        authenticateUser(orgAdmin);
        UpdateProjectWorkspaceRequestDto request = UpdateProjectWorkspaceRequestDto.builder()
                .name("Finance Updated")
                .description("Updated description")
                .build();

        ProjectWorkspaceResponseDto updatedDto = ProjectWorkspaceResponseDto.builder()
                .id(10L)
                .orgCode(TARGET_ORG)
                .name("Finance Updated")
                .description("Updated description")
                .rootFolderId(100L)
                .rootFolderName("Finance Updated")
                .status(ProjectWorkspaceStatus.ACTIVE)
                .build();

        when(projectWorkspaceService.updateWorkspace(eq(10L), any(UpdateProjectWorkspaceRequestDto.class), eq(orgAdmin)))
                .thenReturn(updatedDto);

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Finance Updated")))
                .andExpect(jsonPath("$.description", is("Updated description")));
    }

    // -------------------------------------------------------------------------
    // 5. archive workspace
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("5. Archive workspace returns 200 with ARCHIVED status")
    void archiveWorkspace_Success() throws Exception {
        authenticateUser(orgAdmin);
        ProjectWorkspaceResponseDto archivedDto = ProjectWorkspaceResponseDto.builder()
                .id(10L)
                .orgCode(TARGET_ORG)
                .name("Finance")
                .status(ProjectWorkspaceStatus.ARCHIVED)
                .build();

        when(projectWorkspaceService.archiveWorkspace(eq(10L), eq(orgAdmin)))
                .thenReturn(archivedDto);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/archive", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ARCHIVED")));
    }

    // -------------------------------------------------------------------------
    // 6. restore workspace
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("6. Restore workspace returns 200 with ACTIVE status")
    void restoreWorkspace_Success() throws Exception {
        authenticateUser(orgAdmin);
        ProjectWorkspaceResponseDto restoredDto = ProjectWorkspaceResponseDto.builder()
                .id(10L)
                .orgCode(TARGET_ORG)
                .name("Finance")
                .status(ProjectWorkspaceStatus.ACTIVE)
                .build();

        when(projectWorkspaceService.restoreWorkspace(eq(10L), eq(orgAdmin)))
                .thenReturn(restoredDto);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/restore", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    // -------------------------------------------------------------------------
    // 7. list assignments
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("7. List assignments returns 200 with assignments list")
    void listAssignments_Success() throws Exception {
        authenticateUser(orgAdmin);
        when(projectWorkspaceService.getWorkspaceAssignments(eq(10L), eq(orgAdmin)))
                .thenReturn(List.of(sampleTeamAssignmentDto, sampleUserAssignmentDto));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/assignments", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].teamName", is("Finance Team")))
                .andExpect(jsonPath("$[1].userEmail", is("member@acme.com")));
    }

    // -------------------------------------------------------------------------
    // 8. assign team
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("8. Assign team to workspace returns 201")
    void assignTeam_Success() throws Exception {
        authenticateUser(orgAdmin);
        WorkspaceTeamAssignmentRequestDto request = WorkspaceTeamAssignmentRequestDto.builder()
                .teamId(5L)
                .permissionLevel(PermissionLevel.VIEW)
                .permissionType(PermissionType.ALLOW)
                .build();

        when(projectWorkspaceService.assignTeamToWorkspace(eq(10L), any(WorkspaceTeamAssignmentRequestDto.class), eq(orgAdmin)))
                .thenReturn(sampleTeamAssignmentDto);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/assignments/teams", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissionId", is(50)))
                .andExpect(jsonPath("$.teamId", is(5)))
                .andExpect(jsonPath("$.teamName", is("Finance Team")));
    }

    // -------------------------------------------------------------------------
    // 9. assign user
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("9. Assign user to workspace returns 201")
    void assignUser_Success() throws Exception {
        authenticateUser(orgAdmin);
        WorkspaceUserAssignmentRequestDto request = WorkspaceUserAssignmentRequestDto.builder()
                .userEmail("member@acme.com")
                .permissionLevel(PermissionLevel.EDIT)
                .permissionType(PermissionType.ALLOW)
                .build();

        when(projectWorkspaceService.assignUserToWorkspace(eq(10L), any(WorkspaceUserAssignmentRequestDto.class), eq(orgAdmin)))
                .thenReturn(sampleUserAssignmentDto);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/assignments/users", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissionId", is(51)))
                .andExpect(jsonPath("$.userEmail", is("member@acme.com")))
                .andExpect(jsonPath("$.permissionLevel", is("EDIT")));
    }

    // -------------------------------------------------------------------------
    // 10. revoke assignment
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("10. Revoke assignment returns 204 No Content")
    void revokeAssignment_Success() throws Exception {
        authenticateUser(orgAdmin);
        doNothing().when(projectWorkspaceService).revokeWorkspaceAssignment(eq(10L), eq(50L), eq(orgAdmin));

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/assignments/{permissionId}", 10L, 50L))
                .andExpect(status().isNoContent());

        verify(projectWorkspaceService).revokeWorkspaceAssignment(10L, 50L, orgAdmin);
    }

    // -------------------------------------------------------------------------
    // 11. organization isolation
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("11a. Reject request if body orgCode does not match path orgCode (HTTP 400)")
    void orgIsolation_BodyOrgCodeMismatchPath_Returns400() throws Exception {
        authenticateUser(orgAdmin);
        CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                .orgCode(OTHER_ORG) // Mismatches TARGET_ORG
                .name("Cross Org Workspace")
                .build();

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/workspaces", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not match request body")));

        verify(projectWorkspaceService, never()).createWorkspace(any(), any());
    }

    @Test
    @DisplayName("11b. Reject cross-tenant organization access when service denies access (HTTP 403)")
    void orgIsolation_CrossTenantAccess_Returns403() throws Exception {
        authenticateUser(otherOrgUser);
        when(projectWorkspaceService.getWorkspacesForOrganization(eq(TARGET_ORG), eq(otherOrgUser)))
                .thenThrow(new AccessDeniedException("User is not a member of organization: " + TARGET_ORG));

        mockMvc.perform(get("/api/v1/organizations/{orgCode}/workspaces", TARGET_ORG))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    // -------------------------------------------------------------------------
    // 12. unauthorized regular user
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("12. Regular user without org admin authority is rejected by service with 403")
    void unauthorizedRegularUser_Returns403() throws Exception {
        authenticateUser(regularUser);
        CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                .orgCode(TARGET_ORG)
                .name("Unauthorized Workspace")
                .build();

        when(projectWorkspaceService.createWorkspace(any(), eq(regularUser)))
                .thenThrow(new AccessDeniedException("Organization Admin privileges required in organization: " + TARGET_ORG));

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/workspaces", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Organization Admin privileges required")));
    }

    // -------------------------------------------------------------------------
    // 13. archived workspace behavior
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("13a. Mutating an archived workspace throws IllegalStateException mapped to 400")
    void archivedWorkspace_MutationRejected_Returns400() throws Exception {
        authenticateUser(orgAdmin);
        UpdateProjectWorkspaceRequestDto request = UpdateProjectWorkspaceRequestDto.builder()
                .name("New Name")
                .build();

        when(projectWorkspaceService.updateWorkspace(eq(10L), any(), eq(orgAdmin)))
                .thenThrow(new IllegalStateException("Workspace is archived and cannot be modified"));

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Workspace is archived and cannot be modified")));
    }

    @Test
    @DisplayName("13b. Restoring an already active workspace throws IllegalStateException mapped to 400")
    void activeWorkspace_RestoreRejected_Returns400() throws Exception {
        authenticateUser(orgAdmin);
        when(projectWorkspaceService.restoreWorkspace(eq(10L), eq(orgAdmin)))
                .thenThrow(new IllegalStateException("Workspace is already active: 10"));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/restore", 10L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Workspace is already active")));
    }

    // -------------------------------------------------------------------------
    // 14. invalid workspace/assignment cases
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("14a. Non-existent workspace ID returns 404")
    void nonExistentWorkspace_Returns404() throws Exception {
        authenticateUser(orgAdmin);
        when(projectWorkspaceService.getWorkspaceById(eq(999L), eq(orgAdmin)))
                .thenThrow(new ResourceNotFoundException("Workspace not found with ID: 999"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Workspace not found with ID: 999")));
    }

    @Test
    @DisplayName("14b. Empty workspace name throws IllegalArgumentException mapped to 400")
    void emptyWorkspaceName_Returns400() throws Exception {
        authenticateUser(orgAdmin);
        CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                .name("   ")
                .build();

        when(projectWorkspaceService.createWorkspace(any(), eq(orgAdmin)))
                .thenThrow(new IllegalArgumentException("Workspace name is required"));

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/workspaces", TARGET_ORG)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Workspace name is required")));
    }

    @Test
    @DisplayName("14c. Invalid team assignment missing teamId triggers Bean Validation (400)")
    void invalidTeamAssignment_MissingTeamId_Returns400() throws Exception {
        authenticateUser(orgAdmin);
        // teamId is null, which violates @NotNull
        String jsonBody = "{\"permissionLevel\":\"VIEW\"}";

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/assignments/teams", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.validationErrors[0].field", is("teamId")));
    }

    @Test
    @DisplayName("14d. Invalid user assignment with malformed email triggers Bean Validation (400)")
    void invalidUserAssignment_MalformedEmail_Returns400() throws Exception {
        authenticateUser(orgAdmin);
        String jsonBody = "{\"userEmail\":\"not-an-email\",\"permissionLevel\":\"VIEW\"}";

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/assignments/users", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.validationErrors[0].field", is("userEmail")));
    }

    @Test
    @DisplayName("14e. Assignment target folder outside workspace hierarchy returns 400")
    void targetFolderOutsideWorkspace_Returns400() throws Exception {
        authenticateUser(orgAdmin);
        WorkspaceTeamAssignmentRequestDto request = WorkspaceTeamAssignmentRequestDto.builder()
                .teamId(5L)
                .folderId(999L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(projectWorkspaceService.assignTeamToWorkspace(eq(10L), any(), eq(orgAdmin)))
                .thenThrow(new IllegalArgumentException("Target folder ID 999 is not within workspace root folder hierarchy"));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/assignments/teams", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not within workspace root folder hierarchy")));
    }

    // -------------------------------------------------------------------------
    // 15. response DTO does not expose secrets
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("15. Response DTO does not expose secrets, tokens, or password hashes")
    void responseDto_DoesNotExposeSecrets() throws Exception {
        authenticateUser(orgAdmin);
        when(projectWorkspaceService.getWorkspaceById(eq(10L), eq(orgAdmin)))
                .thenReturn(sampleWorkspaceDto);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.shareLinkToken").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist());

        when(projectWorkspaceService.getWorkspaceAssignments(eq(10L), eq(orgAdmin)))
                .thenReturn(List.of(sampleTeamAssignmentDto));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/assignments", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andExpect(jsonPath("$[0].shareLinkToken").doesNotExist())
                .andExpect(jsonPath("$[0].secret").doesNotExist());
    }

    // -------------------------------------------------------------------------
    // 16. personal workspaces endpoints
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("16a. List personal workspaces returns 200 with personal workspace list")
    void listPersonalWorkspaces_Success() throws Exception {
        authenticateUser(regularUser);
        ProjectWorkspaceResponseDto personalWs = ProjectWorkspaceResponseDto.builder()
                .id(20L)
                .orgCode(null)
                .name("My Personal Project")
                .description("Personal docs")
                .rootFolderId(200L)
                .rootFolderName("My Personal Project")
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdByUserId(regularUser.getId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(projectWorkspaceService.getPersonalWorkspaces(eq(regularUser)))
                .thenReturn(List.of(personalWs));

        mockMvc.perform(get("/api/v1/personal/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(20)))
                .andExpect(jsonPath("$[0].name", is("My Personal Project")))
                .andExpect(jsonPath("$[0].orgCode").doesNotExist())
                .andExpect(jsonPath("$[0].createdByUserId", is(regularUser.getId())));
    }

    @Test
    @DisplayName("16b. Create personal workspace returns 201 with created workspace")
    void createPersonalWorkspace_Success() throws Exception {
        authenticateUser(regularUser);
        CreatePersonalWorkspaceRequestDto request = CreatePersonalWorkspaceRequestDto.builder()
                .name("New Personal Notes")
                .description("My private notes")
                .build();

        ProjectWorkspaceResponseDto created = ProjectWorkspaceResponseDto.builder()
                .id(21L)
                .orgCode(null)
                .name("New Personal Notes")
                .description("My private notes")
                .rootFolderId(201L)
                .rootFolderName("New Personal Notes")
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdByUserId(regularUser.getId())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(projectWorkspaceService.createPersonalWorkspace(any(CreatePersonalWorkspaceRequestDto.class), eq(regularUser)))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/personal/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(21)))
                .andExpect(jsonPath("$.name", is("New Personal Notes")))
                .andExpect(jsonPath("$.orgCode").doesNotExist());
    }

    @Test
    @DisplayName("16c. Create personal workspace with blank name returns 400")
    void createPersonalWorkspace_BlankName_Returns400() throws Exception {
        authenticateUser(regularUser);
        String jsonBody = "{\"name\":\"  \",\"description\":\"Empty name\"}";

        mockMvc.perform(post("/api/v1/personal/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")));
    }

    @Test
    @DisplayName("16d. Assign team to personal workspace returns 400 Bad Request")
    void assignTeamToPersonalWorkspace_Returns400() throws Exception {
        authenticateUser(regularUser);
        WorkspaceTeamAssignmentRequestDto request = WorkspaceTeamAssignmentRequestDto.builder()
                .teamId(5L)
                .folderId(200L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(projectWorkspaceService.assignTeamToWorkspace(eq(20L), any(), eq(regularUser)))
                .thenThrow(new IllegalArgumentException("Teams cannot be assigned to personal workspaces"));

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/assignments/teams", 20L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Teams cannot be assigned to personal workspaces")));
    }
}

