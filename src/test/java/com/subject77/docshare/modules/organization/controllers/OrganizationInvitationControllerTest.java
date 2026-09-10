package com.subject77.docshare.modules.organization.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.exceptions.GlobalExceptionHandler;
import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.organization.dto.CreateOrganizationInvitationRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationInvitationResponseDto;
import com.subject77.docshare.modules.organization.entities.OrganizationInvitationStatus;
import com.subject77.docshare.modules.organization.services.OrganizationInvitationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationInvitationControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private OrganizationInvitationService invitationService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private OrganizationInvitationController invitationController;

    private UserEntity mockUser;
    private static final String USER_ID = "user-org-admin-1";
    private static final String ORG_CODE = "ACME";
    private static final String TOKEN = "valid-invite-token-abc";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUser = UserEntity.builder()
                .id(USER_ID)
                .email("admin@acme.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        org.mockito.Mockito.lenient().when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(invitationController)
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
                Map.of("sub", USER_ID, "email", "admin@acme.com")
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        org.mockito.Mockito.lenient().when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
    }

    // -------------------------------------------------------------
    // POST /api/v1/organizations/{orgCode}/invitations
    // -------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/organizations/{orgCode}/invitations - Success 201 Created")
    void createInvitation_Success() throws Exception {
        setupSecurityContext();

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("newmember@acme.com")
                .role(Role.ROLE_USER)
                .expirationDays(7)
                .build();

        OrganizationInvitationResponseDto response = OrganizationInvitationResponseDto.builder()
                .id(1L)
                .orgCode(ORG_CODE)
                .inviteeEmail("newmember@acme.com")
                .role(Role.ROLE_USER)
                .status(OrganizationInvitationStatus.PENDING)
                .build();

        when(invitationService.createInvitation(eq(ORG_CODE), any(CreateOrganizationInvitationRequestDto.class), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/invitations", ORG_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.orgCode").value(ORG_CODE))
                .andExpect(jsonPath("$.inviteeEmail").value("newmember@acme.com"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(invitationService).createInvitation(eq(ORG_CODE), any(CreateOrganizationInvitationRequestDto.class), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/organizations/{orgCode}/invitations - Validation Failure 400 Bad Request")
    void createInvitation_InvalidBody_Returns400() throws Exception {
        setupSecurityContext();

        // Blank email violates @NotBlank
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("")
                .role(Role.ROLE_USER)
                .build();

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/invitations", ORG_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /api/v1/organizations/{orgCode}/invitations - Forbidden 403")
    void createInvitation_AccessDenied_Returns403() throws Exception {
        setupSecurityContext();

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("newmember@acme.com")
                .role(Role.ROLE_USER)
                .build();

        when(invitationService.createInvitation(eq(ORG_CODE), any(CreateOrganizationInvitationRequestDto.class), any(UserEntity.class)))
                .thenThrow(new AccessDeniedException("User does not have admin permissions"));

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/invitations", ORG_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // -------------------------------------------------------------
    // GET /api/v1/organizations/{orgCode}/invitations
    // -------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/organizations/{orgCode}/invitations - Success 200 OK")
    void getOrganizationPendingInvitations_Success() throws Exception {
        setupSecurityContext();

        OrganizationInvitationResponseDto invite = OrganizationInvitationResponseDto.builder()
                .id(1L)
                .orgCode(ORG_CODE)
                .inviteeEmail("pending@acme.com")
                .status(OrganizationInvitationStatus.PENDING)
                .build();

        when(invitationService.getPendingInvitationsForOrganization(eq(ORG_CODE), any(UserEntity.class)))
                .thenReturn(List.of(invite));

        mockMvc.perform(get("/api/v1/organizations/{orgCode}/invitations", ORG_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].inviteeEmail").value("pending@acme.com"));

        verify(invitationService).getPendingInvitationsForOrganization(eq(ORG_CODE), any(UserEntity.class));
    }

    // -------------------------------------------------------------
    // DELETE /api/v1/organizations/{orgCode}/invitations/{invitationId}
    // -------------------------------------------------------------

    @Test
    @DisplayName("DELETE /api/v1/organizations/{orgCode}/invitations/{id} - Success 204 No Content")
    void revokeInvitation_Success() throws Exception {
        setupSecurityContext();

        doNothing().when(invitationService).revokeInvitation(eq(ORG_CODE), eq(10L), any(UserEntity.class));

        mockMvc.perform(delete("/api/v1/organizations/{orgCode}/invitations/{invitationId}", ORG_CODE, 10L))
                .andExpect(status().isNoContent());

        verify(invitationService).revokeInvitation(eq(ORG_CODE), eq(10L), any(UserEntity.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/organizations/{orgCode}/invitations/{id} - Not Found 404")
    void revokeInvitation_NotFound_Returns404() throws Exception {
        setupSecurityContext();

        doThrow(new ResourceNotFoundException("Invitation not found"))
                .when(invitationService).revokeInvitation(eq(ORG_CODE), eq(999L), any(UserEntity.class));

        mockMvc.perform(delete("/api/v1/organizations/{orgCode}/invitations/{invitationId}", ORG_CODE, 999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/organizations/{orgCode}/invitations/{id} - Expired/IllegalState 400 Bad Request")
    void revokeInvitation_AlreadyExpired_Returns400() throws Exception {
        setupSecurityContext();

        doThrow(new IllegalStateException("Invitation has expired and cannot be revoked"))
                .when(invitationService).revokeInvitation(eq(ORG_CODE), eq(10L), any(UserEntity.class));

        mockMvc.perform(delete("/api/v1/organizations/{orgCode}/invitations/{invitationId}", ORG_CODE, 10L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invitation has expired and cannot be revoked"));
    }

    // -------------------------------------------------------------
    // GET /api/v1/invitations/me
    // -------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/invitations/me - Success 200 OK")
    void getMyPendingInvitations_Success() throws Exception {
        setupSecurityContext();

        OrganizationInvitationResponseDto invite = OrganizationInvitationResponseDto.builder()
                .id(2L)
                .orgCode(ORG_CODE)
                .inviteeEmail("admin@acme.com")
                .status(OrganizationInvitationStatus.PENDING)
                .build();

        when(invitationService.getMyPendingInvitations(any(UserEntity.class)))
                .thenReturn(List.of(invite));

        mockMvc.perform(get("/api/v1/invitations/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2L))
                .andExpect(jsonPath("$[0].orgCode").value(ORG_CODE));

        verify(invitationService).getMyPendingInvitations(any(UserEntity.class));
    }

    // -------------------------------------------------------------
    // GET /api/v1/invitations/token/{token} and /api/v1/invitations/{token}
    // -------------------------------------------------------------

    @Test
    @DisplayName("GET /api/v1/invitations/token/{token} - Success 200 OK")
    void getInvitationByToken_Success() throws Exception {
        OrganizationInvitationResponseDto invite = OrganizationInvitationResponseDto.builder()
                .id(3L)
                .orgCode(ORG_CODE)
                .inviteeEmail("invitee@acme.com")
                .status(OrganizationInvitationStatus.PENDING)
                .build();

        when(invitationService.getInvitationByToken(eq(TOKEN))).thenReturn(invite);

        mockMvc.perform(get("/api/v1/invitations/token/{token}", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3L))
                .andExpect(jsonPath("$.inviteeEmail").value("invitee@acme.com"));

        // Also test legacy alias /api/v1/invitations/{token}
        mockMvc.perform(get("/api/v1/invitations/{token}", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3L));

        verify(invitationService, org.mockito.Mockito.times(2)).getInvitationByToken(eq(TOKEN));
    }

    @Test
    @DisplayName("GET /api/v1/invitations/token/{token} - Not Found 404")
    void getInvitationByToken_NotFound_Returns404() throws Exception {
        when(invitationService.getInvitationByToken(eq("invalid-token")))
                .thenThrow(new ResourceNotFoundException("Invitation not found with token: invalid-token"));

        mockMvc.perform(get("/api/v1/invitations/token/{token}", "invalid-token"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------
    // POST /api/v1/invitations/token/{token}/accept
    // -------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/invitations/token/{token}/accept - Success 200 OK")
    void acceptInvitation_Success() throws Exception {
        setupSecurityContext();

        OrganizationInvitationResponseDto response = OrganizationInvitationResponseDto.builder()
                .id(4L)
                .orgCode(ORG_CODE)
                .status(OrganizationInvitationStatus.ACCEPTED)
                .build();

        when(invitationService.acceptInvitation(eq(TOKEN), any(UserEntity.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/invitations/token/{token}/accept", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4L))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(invitationService).acceptInvitation(eq(TOKEN), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/invitations/token/{token}/accept - Already Accepted / IllegalState 400")
    void acceptInvitation_IllegalState_Returns400() throws Exception {
        setupSecurityContext();

        when(invitationService.acceptInvitation(eq(TOKEN), any(UserEntity.class)))
                .thenThrow(new IllegalStateException("Invitation is not pending (status: ACCEPTED)"));

        mockMvc.perform(post("/api/v1/invitations/token/{token}/accept", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invitation is not pending (status: ACCEPTED)"));
    }

    // -------------------------------------------------------------
    // POST /api/v1/invitations/token/{token}/decline
    // -------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/invitations/token/{token}/decline - Success 200 OK")
    void declineInvitation_Success() throws Exception {
        setupSecurityContext();

        OrganizationInvitationResponseDto response = OrganizationInvitationResponseDto.builder()
                .id(5L)
                .orgCode(ORG_CODE)
                .status(OrganizationInvitationStatus.DECLINED)
                .build();

        when(invitationService.declineInvitation(eq(TOKEN), any(UserEntity.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/invitations/token/{token}/decline", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L))
                .andExpect(jsonPath("$.status").value("DECLINED"));

        verify(invitationService).declineInvitation(eq(TOKEN), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/invitations/token/{token}/decline - Expired 400 Bad Request")
    void declineInvitation_Expired_Returns400() throws Exception {
        setupSecurityContext();

        when(invitationService.declineInvitation(eq(TOKEN), any(UserEntity.class)))
                .thenThrow(new IllegalStateException("Invitation has expired"));

        mockMvc.perform(post("/api/v1/invitations/token/{token}/decline", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invitation has expired"));
    }
}
