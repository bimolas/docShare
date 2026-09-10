package com.subject77.docshare.modules.organization.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.exceptions.GlobalExceptionHandler;
import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.organization.dto.TransferManagerRequestDto;
import com.subject77.docshare.modules.organization.dto.UpdateGroupMemberRoleRequestDto;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.services.GroupService;
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
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GroupService groupService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private GroupController groupController;

    private UserEntity mockUser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ORG_CODE = "ACME";
    private static final Long GROUP_ID = 10L;
    private static final String USER_ID = "user-123";
    private static final String TARGET_USER_ID = "user-456";

    @BeforeEach
    void setUp() {
        mockUser = UserEntity.builder()
                .id(USER_ID)
                .email("admin@acme.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.ROLE_USER)
                .build();

        org.mockito.Mockito.lenient().when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(groupController)
                .setCustomArgumentResolvers(resolver, new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Jwt jwt = createJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        org.mockito.Mockito.lenient().when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Jwt createJwt() {
        return new Jwt(
                "token-string",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("sub", USER_ID, "email", "admin@acme.com")
        );
    }

    @Test
    @DisplayName("PUT /api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}/role - MEMBER to MANAGER returns 200 OK")
    void updateMemberRole_MemberToManager_ReturnsOk() throws Exception {
        doNothing().when(groupService).updateMemberRole(eq(ORG_CODE), eq(GROUP_ID), eq(TARGET_USER_ID), eq(GroupMemberRole.MANAGER), any(UserEntity.class));

        UpdateGroupMemberRoleRequestDto request = UpdateGroupMemberRoleRequestDto.builder()
                .role(GroupMemberRole.MANAGER)
                .build();

        mockMvc.perform(put("/api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}/role", ORG_CODE, GROUP_ID, TARGET_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}/role - MANAGER to MEMBER returns 400 Bad Request")
    void updateMemberRole_ManagerToMember_ReturnsBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("Cannot demote Team Manager to Member directly. A Team must maintain an active Team Manager. Use transferTeamManager to assign a successor."))
                .when(groupService).updateMemberRole(eq(ORG_CODE), eq(GROUP_ID), eq(TARGET_USER_ID), eq(GroupMemberRole.MEMBER), any(UserEntity.class));

        UpdateGroupMemberRoleRequestDto request = UpdateGroupMemberRoleRequestDto.builder()
                .role(GroupMemberRole.MEMBER)
                .build();

        mockMvc.perform(put("/api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}/role", ORG_CODE, GROUP_ID, TARGET_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cannot demote Team Manager to Member directly")));
    }

    @Test
    @DisplayName("PUT /api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}/role - Unauthorized caller returns 403 Forbidden")
    void updateMemberRole_UnauthorizedCaller_ReturnsForbidden() throws Exception {
        doThrow(new AccessDeniedException("Team Manager or Organization Admin privileges required for group: " + GROUP_ID))
                .when(groupService).updateMemberRole(eq(ORG_CODE), eq(GROUP_ID), eq(TARGET_USER_ID), eq(GroupMemberRole.MANAGER), any(UserEntity.class));

        UpdateGroupMemberRoleRequestDto request = UpdateGroupMemberRoleRequestDto.builder()
                .role(GroupMemberRole.MANAGER)
                .build();

        mockMvc.perform(put("/api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}/role", ORG_CODE, GROUP_ID, TARGET_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("privileges required")));
    }

    @Test
    @DisplayName("PUT /api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}/role - Target not found returns 404 Not Found")
    void updateMemberRole_TargetNotFound_ReturnsNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("User is not a member of group " + GROUP_ID))
                .when(groupService).updateMemberRole(eq(ORG_CODE), eq(GROUP_ID), eq(TARGET_USER_ID), eq(GroupMemberRole.MANAGER), any(UserEntity.class));

        UpdateGroupMemberRoleRequestDto request = UpdateGroupMemberRoleRequestDto.builder()
                .role(GroupMemberRole.MANAGER)
                .build();

        mockMvc.perform(put("/api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}/role", ORG_CODE, GROUP_ID, TARGET_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not a member of group")));
    }

    @Test
    @DisplayName("PUT /api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}/role - Null role returns 400 Bad Request")
    void updateMemberRole_NullRole_ReturnsBadRequest() throws Exception {
        String invalidJson = "{\"role\": null}";

        mockMvc.perform(put("/api/v1/organizations/{orgCode}/groups/{groupId}/members/{userId}/role", ORG_CODE, GROUP_ID, TARGET_USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/organizations/{orgCode}/groups/{groupId}/transfer-manager - Canonical transfer returns 200 OK")
    void transferTeamManager_ReturnsOk() throws Exception {
        doNothing().when(groupService).transferTeamManager(eq(ORG_CODE), eq(GROUP_ID), eq(TARGET_USER_ID), any(UserEntity.class));

        TransferManagerRequestDto request = TransferManagerRequestDto.builder()
                .newManagerUserId(TARGET_USER_ID)
                .build();

        mockMvc.perform(post("/api/v1/organizations/{orgCode}/groups/{groupId}/transfer-manager", ORG_CODE, GROUP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
