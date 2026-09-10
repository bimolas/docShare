package com.subject77.docshare.modules.permission.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.modules.permission.dto.BulkSelectedMemberShareDto;
import com.subject77.docshare.modules.permission.dto.BulkShareWithExclusionsDto;
import com.subject77.docshare.modules.permission.dto.EffectiveAccessAuditDto;
import com.subject77.docshare.modules.permission.dto.EffectiveAccessEntryDto;
import com.subject77.docshare.modules.permission.dto.ManageAccessSummaryDto;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.dto.PermissionUpdateRequestDto;
import com.subject77.docshare.modules.permission.dto.SharedWithMeResponseDto;
import com.subject77.docshare.modules.permission.dto.SharedByMeResponseDto;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.services.PermissionService;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.subject77.docshare.exceptions.GlobalExceptionHandler;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PermissionControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private PermissionService permissionService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private PermissionController permissionController;

    private UserEntity mockUser;
    private static final String USER_ID = "usr-test-100";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUser = UserEntity.builder()
                .id(USER_ID)
                .email("admin@org.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        lenient().when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(permissionController)
                .setCustomArgumentResolvers(resolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext() {
        Jwt jwt = new Jwt(
                "token-string",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", USER_ID, "email", "admin@org.com")
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
    }

    @Test
    @DisplayName("POST /api/v1/permissions should grant permission and return 201 CREATED")
    void grantPermission_ShouldReturn201() throws Exception {
        setupSecurityContext();

        PermissionRequestDto requestDto = PermissionRequestDto.builder()
                .folderId(100L)
                .permissionLevel(PermissionLevel.VIEW)
                .permissionType(PermissionType.ALLOW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("target@org.com")
                .build();

        PermissionResponseDto responseDto = PermissionResponseDto.builder()
                .id(1L)
                .folderId(100L)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("target@org.com")
                .grantedByUserId(USER_ID)
                .build();

        when(permissionService.grantPermission(any(PermissionRequestDto.class), eq(USER_ID)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.permissionLevel").value("VIEW"))
                .andExpect(jsonPath("$.targetUserEmail").value("target@org.com"));

        verify(permissionService).grantPermission(any(PermissionRequestDto.class), eq(USER_ID));
    }

    @Test
    @DisplayName("POST /api/v1/permissions/grant-with-exclusions should return 201 CREATED")
    void grantFolderShareWithExclusions_ShouldReturn201() throws Exception {
        setupSecurityContext();

        BulkShareWithExclusionsDto requestDto = BulkShareWithExclusionsDto.builder()
                .folderId(200L)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("user@org.com")
                .excludedDocumentIds(List.of(101L))
                .build();

        PermissionResponseDto responseDto = PermissionResponseDto.builder()
                .id(2L)
                .folderId(200L)
                .permissionLevel(PermissionLevel.EDIT)
                .targetUserEmail("user@org.com")
                .build();

        when(permissionService.grantFolderShareWithExclusions(any(BulkShareWithExclusionsDto.class), eq(USER_ID)))
                .thenReturn(List.of(responseDto));

        mockMvc.perform(post("/api/v1/permissions/grant-with-exclusions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(2L))
                .andExpect(jsonPath("$[0].folderId").value(200L));

        verify(permissionService).grantFolderShareWithExclusions(any(BulkShareWithExclusionsDto.class), eq(USER_ID));
    }

    @Test
    @DisplayName("POST /api/v1/permissions/grant-selected-members should return 201 CREATED")
    void grantSelectedMembers_ShouldReturn201() throws Exception {
        setupSecurityContext();

        BulkSelectedMemberShareDto.MemberShareItemDto item1 = BulkSelectedMemberShareDto.MemberShareItemDto.builder()
                .targetUserEmail("m1@org.com")
                .permissionLevel(PermissionLevel.VIEW)
                .build();
        BulkSelectedMemberShareDto.MemberShareItemDto item2 = BulkSelectedMemberShareDto.MemberShareItemDto.builder()
                .targetUserEmail("m2@org.com")
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        BulkSelectedMemberShareDto requestDto = BulkSelectedMemberShareDto.builder()
                .targetOrgCode("ORG-ALPHA")
                .folderId(200L)
                .selectedMembers(List.of(item1, item2))
                .build();

        PermissionResponseDto r1 = PermissionResponseDto.builder().id(3L).targetUserEmail("m1@org.com").build();
        PermissionResponseDto r2 = PermissionResponseDto.builder().id(4L).targetUserEmail("m2@org.com").build();

        when(permissionService.grantSelectedMembers(any(BulkSelectedMemberShareDto.class), eq(USER_ID)))
                .thenReturn(List.of(r1, r2));

        mockMvc.perform(post("/api/v1/permissions/grant-selected-members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        verify(permissionService).grantSelectedMembers(any(BulkSelectedMemberShareDto.class), eq(USER_ID));
    }

    @Test
    @DisplayName("PUT /api/v1/permissions/{id} should update permission and return 200 OK")
    void updatePermission_ShouldReturn200() throws Exception {
        setupSecurityContext();

        PermissionUpdateRequestDto updateDto = PermissionUpdateRequestDto.builder()
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        PermissionResponseDto responseDto = PermissionResponseDto.builder()
                .id(10L)
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        when(permissionService.updatePermission(eq(10L), any(PermissionUpdateRequestDto.class), eq(USER_ID)))
                .thenReturn(responseDto);

        mockMvc.perform(put("/api/v1/permissions/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.permissionLevel").value("EDIT"));

        verify(permissionService).updatePermission(eq(10L), any(PermissionUpdateRequestDto.class), eq(USER_ID));
    }

    @Test
    @DisplayName("PATCH /api/v1/permissions/{id} should patch permission and return 200 OK")
    void patchPermission_ShouldReturn200() throws Exception {
        setupSecurityContext();

        PermissionUpdateRequestDto updateDto = PermissionUpdateRequestDto.builder()
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        PermissionResponseDto responseDto = PermissionResponseDto.builder()
                .id(10L)
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        when(permissionService.updatePermission(eq(10L), any(PermissionUpdateRequestDto.class), eq(USER_ID)))
                .thenReturn(responseDto);

        mockMvc.perform(patch("/api/v1/permissions/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.permissionLevel").value("EDIT"));

        verify(permissionService).updatePermission(eq(10L), any(PermissionUpdateRequestDto.class), eq(USER_ID));
    }

    @Test
    @DisplayName("DELETE /api/v1/permissions/{id} should revoke permission and return 200 OK")
    void revokePermission_ShouldReturn200() throws Exception {
        setupSecurityContext();

        PermissionResponseDto responseDto = PermissionResponseDto.builder()
                .id(10L)
                .revoked(true)
                .build();

        when(permissionService.revokePermission(eq(10L), eq(USER_ID)))
                .thenReturn(responseDto);

        mockMvc.perform(delete("/api/v1/permissions/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.revoked").value(true));

        verify(permissionService).revokePermission(eq(10L), eq(USER_ID));
    }

    @Test
    @DisplayName("DELETE /api/v1/permissions/{id} with null currentUser should return 403 and NOT call permissionService")
    void revokePermission_NullUser_ShouldReturn403Forbidden() throws Exception {
        // No security context setup -> currentUser resolves to null
        mockMvc.perform(delete("/api/v1/permissions/10"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(permissionService);
    }

    @Test
    @DisplayName("GET /api/v1/permissions/{id} should return permission by ID")
    void getPermissionById_ShouldReturn200() throws Exception {
        PermissionResponseDto responseDto = PermissionResponseDto.builder()
                .id(10L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(permissionService.getPermissionById(eq(10L), any())).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/permissions/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L));
    }

    @Test
    @DisplayName("GET /api/v1/permissions/document/{documentId} should return active permissions for document")
    void getActivePermissionsForDocument_ShouldReturn200() throws Exception {
        PermissionResponseDto responseDto = PermissionResponseDto.builder()
                .id(10L)
                .documentId(100L)
                .build();

        when(permissionService.getActivePermissionsForDocument(eq(100L), any())).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/api/v1/permissions/document/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(100L));
    }

    @Test
    @DisplayName("GET /api/v1/permissions/folder/{folderId} should return active permissions for folder")
    void getActivePermissionsForFolder_ShouldReturn200() throws Exception {
        PermissionResponseDto responseDto = PermissionResponseDto.builder()
                .id(10L)
                .folderId(200L)
                .build();

        when(permissionService.getActivePermissionsForFolder(eq(200L), any())).thenReturn(List.of(responseDto));

        mockMvc.perform(get("/api/v1/permissions/folder/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].folderId").value(200L));
    }

    @Test
    @DisplayName("GET /api/v1/permissions/shared-with-me should return shared items")
    void getSharedWithMe_ShouldReturn200() throws Exception {
        setupSecurityContext();

        SharedWithMeResponseDto responseDto = SharedWithMeResponseDto.builder()
                .sharedDocuments(Collections.emptyList())
                .sharedFolders(Collections.emptyList())
                .build();

        when(permissionService.getSharedWithMe(any(UserEntity.class))).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/permissions/shared-with-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharedDocuments").isArray())
                .andExpect(jsonPath("$.sharedFolders").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/permissions/shared-by-me should return items shared by user")
    void getSharedByMe_ShouldReturn200() throws Exception {
        setupSecurityContext();

        SharedByMeResponseDto responseDto = SharedByMeResponseDto.builder()
                .sharedDocuments(Collections.emptyList())
                .sharedFolders(Collections.emptyList())
                .build();

        when(permissionService.getSharedByMe(any(UserEntity.class), eq(true))).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/permissions/shared-by-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharedDocuments").isArray())
                .andExpect(jsonPath("$.sharedFolders").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/permissions/effective should return effective permissions audit")
    void getEffectivePermissions_ShouldReturn200() throws Exception {
        setupSecurityContext();

        EffectiveAccessAuditDto responseDto = EffectiveAccessAuditDto.builder()
                .resourceId(100L)
                .resourceType("DOCUMENT")
                .resourceName("ImportantDoc.pdf")
                .build();

        when(permissionService.getEffectivePermissions(eq(100L), eq("DOCUMENT"), any(UserEntity.class)))
                .thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/permissions/effective")
                        .param("targetId", "100")
                        .param("targetType", "DOCUMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value(100L))
                .andExpect(jsonPath("$.resourceName").value("ImportantDoc.pdf"));
    }

    @Test
    @DisplayName("GET /api/v1/permissions/manage should return ManageAccessSummaryDto for resource")
    void getManageAccessSummary_ShouldReturn200() throws Exception {
        setupSecurityContext();

        EffectiveAccessEntryDto entry = EffectiveAccessEntryDto.builder()
                .principalId("usr-1")
                .principalType("USER")
                .displayName("John Doe")
                .email("john@org.com")
                .permissionLevel(PermissionLevel.EDIT)
                .source("DIRECT")
                .status("ACTIVE")
                .external(false)
                .build();

        ManageAccessSummaryDto summaryDto = ManageAccessSummaryDto.builder()
                .resourceId(100L)
                .resourceType("DOCUMENT")
                .resourceName("ImportantDoc.pdf")
                .ownerEmail("admin@org.com")
                .entries(List.of(entry))
                .build();

        when(permissionService.getManageAccessSummary(eq(100L), eq("DOCUMENT"), any(UserEntity.class)))
                .thenReturn(summaryDto);

        mockMvc.perform(get("/api/v1/permissions/manage")
                        .param("resourceId", "100")
                        .param("resourceType", "DOCUMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value(100L))
                .andExpect(jsonPath("$.resourceType").value("DOCUMENT"))
                .andExpect(jsonPath("$.resourceName").value("ImportantDoc.pdf"))
                .andExpect(jsonPath("$.ownerEmail").value("admin@org.com"))
                .andExpect(jsonPath("$.entries[0].principalId").value("usr-1"))
                .andExpect(jsonPath("$.entries[0].source").value("DIRECT"));

        verify(permissionService).getManageAccessSummary(eq(100L), eq("DOCUMENT"), any(UserEntity.class));
    }
}
