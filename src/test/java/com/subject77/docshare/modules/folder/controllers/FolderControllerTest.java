package com.subject77.docshare.modules.folder.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.exceptions.GlobalExceptionHandler;
import com.subject77.docshare.modules.folder.dto.FolderRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.services.FolderService;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FolderControllerTest {

    private static final String ORG_CODE = "ACME";

    @Mock
    private FolderService folderService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private FolderController folderController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UserEntity orgAdmin;
    private UserEntity orgManager;
    private UserEntity regularUser;

    private FolderResponseDto sampleSubspaceResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        lenient().when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(folderController)
                .setCustomArgumentResolvers(resolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        OrganizationEntity acmeOrg = OrganizationEntity.builder()
                .orgCode(ORG_CODE)
                .name("Acme Corp")
                .build();

        orgAdmin = UserEntity.builder()
                .id("usr-admin")
                .email("admin@acme.com")
                .role(Role.ROLE_ORG_ADMIN)
                .organization(acmeOrg)
                .build();

        orgManager = UserEntity.builder()
                .id("usr-mgr")
                .email("mgr@acme.com")
                .role(Role.ROLE_ORG_MANAGER)
                .organization(acmeOrg)
                .build();

        regularUser = UserEntity.builder()
                .id("usr-member")
                .email("member@acme.com")
                .role(Role.ROLE_USER)
                .organization(acmeOrg)
                .build();

        sampleSubspaceResponse = FolderResponseDto.builder()
                .id(100L)
                .name("Engineering")
                .parentFolderId(null)
                .ownerOrgCode(ORG_CODE)
                .createdByUserId("usr-admin")
                .createdAt(Instant.parse("2026-09-05T10:00:00Z"))
                .subspaceType(SubspaceType.ORGANIZATION_GENERAL)
                .isDefaultSubspace(false)
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
    // POST /api/v1/organizations/{orgCode}/subspaces
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /subspaces returns 201 and FolderResponseDto when ORG_ADMIN creates subspace")
    void createSubspace_Admin_Returns201() throws Exception {
        authenticateUser(orgAdmin);

        FolderRequestDto request = FolderRequestDto.builder()
                .name("Engineering")
                .build();

        when(folderService.createSubspace(eq(ORG_CODE), any(FolderRequestDto.class), eq(orgAdmin)))
                .thenReturn(sampleSubspaceResponse);

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/subspaces", ORG_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(100)))
                .andExpect(jsonPath("$.name", is("Engineering")))
                .andExpect(jsonPath("$.parentFolderId", nullValue()))
                .andExpect(jsonPath("$.ownerOrgCode", is(ORG_CODE)))
                .andExpect(jsonPath("$.createdByUserId", is("usr-admin")))
                .andExpect(jsonPath("$.subspaceType", is("ORGANIZATION_GENERAL")))
                .andExpect(jsonPath("$.defaultSubspace", is(false)));
    }

    @Test
    @DisplayName("POST /subspaces returns 403 when ORG_MANAGER attempts to create subspace")
    void createSubspace_Manager_Returns403() throws Exception {
        authenticateUser(orgManager);

        FolderRequestDto request = FolderRequestDto.builder()
                .name("Marketing")
                .build();

        when(folderService.createSubspace(eq(ORG_CODE), any(FolderRequestDto.class), eq(orgManager)))
                .thenThrow(new AccessDeniedException("User does not have admin permissions in organization: " + ORG_CODE));

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/subspaces", ORG_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /subspaces returns 403 when regular USER attempts to create subspace")
    void createSubspace_RegularUser_Returns403() throws Exception {
        authenticateUser(regularUser);

        FolderRequestDto request = FolderRequestDto.builder()
                .name("Marketing")
                .build();

        when(folderService.createSubspace(eq(ORG_CODE), any(FolderRequestDto.class), eq(regularUser)))
                .thenThrow(new AccessDeniedException("User does not have admin permissions in organization: " + ORG_CODE));

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/subspaces", ORG_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /subspaces returns 400 when name is blank")
    void createSubspace_BlankName_Returns400() throws Exception {
        authenticateUser(orgAdmin);

        FolderRequestDto request = FolderRequestDto.builder()
                .name("   ")
                .build();

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/subspaces", ORG_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(folderService);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/organizations/{orgCode}/subspaces
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /subspaces returns 200 with list of FolderResponseDto")
    void getOrganizationSubspaces_Returns200() throws Exception {
        authenticateUser(orgAdmin);

        when(folderService.getOrganizationSubspaces(eq(ORG_CODE), eq(orgAdmin)))
                .thenReturn(List.of(sampleSubspaceResponse));

        mockMvc.perform(get("/api/v1/organizations/{orgCode}/subspaces", ORG_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(100)))
                .andExpect(jsonPath("$[0].name", is("Engineering")))
                .andExpect(jsonPath("$[0].parentFolderId", nullValue()))
                .andExpect(jsonPath("$[0].ownerOrgCode", is(ORG_CODE)))
                .andExpect(jsonPath("$[0].createdByUserId", is("usr-admin")))
                .andExpect(jsonPath("$[0].subspaceType", is("ORGANIZATION_GENERAL")))
                .andExpect(jsonPath("$[0].defaultSubspace", is(false)));
    }

    @Test
    @DisplayName("GET /subspaces returns 403 when caller is from different organization")
    void getOrganizationSubspaces_CrossOrg_Returns403() throws Exception {
        authenticateUser(regularUser);

        when(folderService.getOrganizationSubspaces(eq("OTHER_ORG"), eq(regularUser)))
                .thenThrow(new AccessDeniedException("User is not a member of organization: OTHER_ORG"));

        mockMvc.perform(get("/api/v1/organizations/{orgCode}/subspaces", "OTHER_ORG"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/organizations/{orgCode}/folders
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /folders with parentFolderId == null delegates to canonical workspace creation and returns 201")
    void createFolder_NullParent_Returns201() throws Exception {
        authenticateUser(orgAdmin);

        FolderRequestDto request = FolderRequestDto.builder()
                .name("New Root Workspace")
                .parentFolderId(null)
                .build();

        when(folderService.createFolder(eq(ORG_CODE), any(FolderRequestDto.class), eq(orgAdmin)))
                .thenReturn(sampleSubspaceResponse);

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/folders", ORG_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(100)))
                .andExpect(jsonPath("$.name", is("Engineering")));

        verify(folderService).createFolder(eq(ORG_CODE), any(FolderRequestDto.class), eq(orgAdmin));
    }

    @Test
    @DisplayName("POST /folders with parentFolderId != null creates child folder and returns 201")
    void createFolder_WithParent_Returns201() throws Exception {
        authenticateUser(regularUser);

        FolderRequestDto request = FolderRequestDto.builder()
                .name("Child Folder")
                .parentFolderId(100L)
                .build();

        FolderResponseDto childResponse = FolderResponseDto.builder()
                .id(200L)
                .name("Child Folder")
                .parentFolderId(100L)
                .ownerOrgCode(ORG_CODE)
                .createdByUserId("usr-member")
                .createdAt(Instant.parse("2026-09-05T10:30:00Z"))
                .build();

        when(folderService.createFolder(eq(ORG_CODE), any(FolderRequestDto.class), eq(regularUser)))
                .thenReturn(childResponse);

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/folders", ORG_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(200)))
                .andExpect(jsonPath("$.name", is("Child Folder")))
                .andExpect(jsonPath("$.parentFolderId", is(100)));

        verify(folderService).createFolder(eq(ORG_CODE), any(FolderRequestDto.class), eq(regularUser));
    }
}
