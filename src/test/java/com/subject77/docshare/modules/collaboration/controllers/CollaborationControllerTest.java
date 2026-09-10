package com.subject77.docshare.modules.collaboration.controllers;

import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.modules.collaboration.dto.CollaborationParticipantResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CollaborationResponseDto;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.services.CollaborationService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.services.UserService;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CollaborationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CollaborationService collaborationService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private CollaborationController collaborationController;

    private UserEntity mockUser;
    private static final String USER_ID = "11111111-2222-3333-4444-555555555555";

    @BeforeEach
    void setUp() {
        mockUser = UserEntity.builder()
                .id(USER_ID)
                .email("admin@partner.com")
                .firstName("Partner")
                .lastName("Admin")
                .role(Role.ROLE_USER)
                .build();

        when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(collaborationController)
                .setCustomArgumentResolvers(resolver)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/collaborations/{id}/accept should call acceptCollaboration and return 200 OK")
    void acceptCollaboration_ShouldCallServiceAndReturn200() throws Exception {
        setupSecurityContext();

        CollaborationResponseDto response = CollaborationResponseDto.builder()
                .id(100L)
                .name("Project Alpha")
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationService.acceptCollaboration(eq(100L), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/collaborations/100/accept")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(collaborationService).acceptCollaboration(eq(100L), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/collaborations/{id}/decline should call declineCollaboration and return 200 OK")
    void declineCollaboration_ShouldCallServiceAndReturn200() throws Exception {
        setupSecurityContext();

        CollaborationResponseDto response = CollaborationResponseDto.builder()
                .id(100L)
                .name("Project Alpha")
                .status(CollaborationStatus.REVOKED)
                .build();

        when(collaborationService.declineCollaboration(eq(100L), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/collaborations/100/decline")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.status").value("REVOKED"));

        verify(collaborationService).declineCollaboration(eq(100L), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/collaborations/{id}/participants should add participant and return 201 CREATED")
    void addParticipant_ShouldCallServiceAndReturn201() throws Exception {
        setupSecurityContext();

        CollaborationParticipantResponseDto response = CollaborationParticipantResponseDto.builder()
                .id(50L)
                .collaborationId(100L)
                .userId("target-user-id")
                .userEmail("target@org.com")
                .build();

        when(collaborationService.addParticipant(eq(100L), eq("target-user-id"), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/collaborations/100/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"target-user-id\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50L))
                .andExpect(jsonPath("$.userId").value("target-user-id"));

        verify(collaborationService).addParticipant(eq(100L), eq("target-user-id"), any(UserEntity.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/collaborations/{id}/participants/{userId} should remove participant and return 204 NO_CONTENT")
    void removeParticipant_ShouldCallServiceAndReturn204() throws Exception {
        setupSecurityContext();

        doNothing().when(collaborationService).removeParticipant(eq(100L), eq("target-user-id"), any(UserEntity.class));

        mockMvc.perform(delete("/api/v1/collaborations/100/participants/target-user-id"))
                .andExpect(status().isNoContent());

        verify(collaborationService).removeParticipant(eq(100L), eq("target-user-id"), any(UserEntity.class));
    }

    @Test
    @DisplayName("GET /api/v1/collaborations/{id}/participants should return 200 OK with list of participants")
    void getParticipants_ShouldCallServiceAndReturn200() throws Exception {
        setupSecurityContext();

        CollaborationParticipantResponseDto participant = CollaborationParticipantResponseDto.builder()
                .id(50L)
                .collaborationId(100L)
                .userId("target-user-id")
                .userEmail("target@org.com")
                .build();

        when(collaborationService.getParticipants(eq(100L), any(UserEntity.class)))
                .thenReturn(List.of(participant));

        mockMvc.perform(get("/api/v1/collaborations/100/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(50L))
                .andExpect(jsonPath("$[0].userId").value("target-user-id"));

        verify(collaborationService).getParticipants(eq(100L), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/collaborations/{id}/permissions should grant permission and return 201 CREATED")
    void grantCollaborationPermission_ShouldCallServiceAndReturn201() throws Exception {
        setupSecurityContext();

        PermissionResponseDto response = PermissionResponseDto.builder()
                .id(1L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .folderId(200L)
                .targetUserEmail("partner@org.com")
                .build();

        when(collaborationService.grantCollaborationPermission(eq(100L), any(PermissionRequestDto.class), any(UserEntity.class)))
                .thenReturn(response);

        String jsonRequest = """
                {
                    "permissionType": "ALLOW",
                    "permissionLevel": "VIEW",
                    "recipientType": "USER_PERSONAL",
                    "folderId": 200,
                    "targetUserEmail": "partner@org.com"
                }
                """;

        mockMvc.perform(post("/api/v1/collaborations/100/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.permissionType").value("ALLOW"))
                .andExpect(jsonPath("$.permissionLevel").value("VIEW"))
                .andExpect(jsonPath("$.folderId").value(200L))
                .andExpect(jsonPath("$.targetUserEmail").value("partner@org.com"));

        verify(collaborationService).grantCollaborationPermission(eq(100L), any(PermissionRequestDto.class), any(UserEntity.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/collaborations/{id}/permissions/{permissionId} should revoke permission and return 200 OK")
    void revokeCollaborationPermission_ShouldCallServiceAndReturn200() throws Exception {
        setupSecurityContext();

        PermissionResponseDto response = PermissionResponseDto.builder()
                .id(1L)
                .revoked(true)
                .revokedByUserId(USER_ID)
                .build();

        when(collaborationService.revokeCollaborationPermission(eq(100L), eq(1L), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(delete("/api/v1/collaborations/100/permissions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.revoked").value(true));

        verify(collaborationService).revokeCollaborationPermission(eq(100L), eq(1L), any(UserEntity.class));
    }

    @Test
    @DisplayName("GET /api/v1/collaborations/{id}/permissions should return 200 OK with list of permissions")
    void getCollaborationPermissions_ShouldCallServiceAndReturn200() throws Exception {
        setupSecurityContext();

        PermissionResponseDto response = PermissionResponseDto.builder()
                .id(1L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .folderId(200L)
                .targetUserEmail("partner@org.com")
                .build();

        when(collaborationService.getCollaborationPermissions(eq(100L), any(UserEntity.class)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/collaborations/100/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].permissionType").value("ALLOW"))
                .andExpect(jsonPath("$[0].permissionLevel").value("VIEW"))
                .andExpect(jsonPath("$[0].folderId").value(200L));

        verify(collaborationService).getCollaborationPermissions(eq(100L), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/collaborations/{id}/revoke should call revokeCollaboration and return 200 OK")
    void revokeCollaboration_ShouldCallServiceAndReturn200() throws Exception {
        setupSecurityContext();

        CollaborationResponseDto response = CollaborationResponseDto.builder()
                .id(100L)
                .name("Project Alpha")
                .status(CollaborationStatus.REVOKED)
                .build();

        when(collaborationService.revokeCollaboration(eq(100L), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/collaborations/100/revoke")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.status").value("REVOKED"));

        verify(collaborationService).revokeCollaboration(eq(100L), any(UserEntity.class));
    }

    private void setupSecurityContext() {
        Jwt jwt = createJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
    }

    private Jwt createJwt() {
        return new Jwt(
                "token-string",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", USER_ID, "email", "admin@partner.com")
        );
    }
}


