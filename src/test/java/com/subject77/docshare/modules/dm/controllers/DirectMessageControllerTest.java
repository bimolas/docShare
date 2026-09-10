package com.subject77.docshare.modules.dm.controllers;

import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.modules.dm.dto.*;
import com.subject77.docshare.modules.dm.services.DirectMessageService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DirectMessageControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DirectMessageService directMessageService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private DirectMessageController directMessageController;

    private UserEntity mockUser;
    private static final String USER_ID = "usr-1111-2222";

    @BeforeEach
    void setUp() {
        mockUser = UserEntity.builder()
                .id(USER_ID)
                .email("user1@example.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.ROLE_USER)
                .build();

        when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(directMessageController)
                .setCustomArgumentResolvers(resolver, new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
                Map.of("sub", USER_ID, "email", "user1@example.com")
        );
    }

    @Test
    @DisplayName("POST /api/v1/direct-messages/threads should initiate or get thread and return 201 CREATED")
    void initiateOrGetThread_ShouldReturn201() throws Exception {
        setupSecurityContext();

        DirectMessageThreadResponseDto response = DirectMessageThreadResponseDto.builder()
                .id(100L)
                .unreadCount(0L)
                .build();

        when(directMessageService.initiateOrGetThread(any(UserEntity.class), any(InitiateThreadRequestDto.class)))
                .thenReturn(response);

        String requestJson = """
                {
                    "recipientUserId": "usr-3333-4444"
                }
                """;

        mockMvc.perform(post("/api/v1/direct-messages/threads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.unreadCount").value(0L));

        verify(directMessageService).initiateOrGetThread(any(UserEntity.class), any(InitiateThreadRequestDto.class));
    }

    @Test
    @DisplayName("GET /api/v1/direct-messages/threads should return 200 OK")
    void getUserThreads_ShouldReturn200() throws Exception {
        setupSecurityContext();

        DirectMessageThreadPageResponseDto response = DirectMessageThreadPageResponseDto.builder()
                .threads(List.of(DirectMessageThreadResponseDto.builder().id(100L).build()))
                .totalElements(1)
                .build();

        when(directMessageService.getUserThreads(any(UserEntity.class), any(Pageable.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/direct-messages/threads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.threads[0].id").value(100L));

        verify(directMessageService).getUserThreads(any(UserEntity.class), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/direct-messages/threads/{threadId} should return 200 OK")
    void getThread_ShouldReturn200() throws Exception {
        setupSecurityContext();

        DirectMessageThreadResponseDto response = DirectMessageThreadResponseDto.builder()
                .id(100L)
                .unreadCount(2L)
                .build();

        when(directMessageService.getThread(any(UserEntity.class), eq(100L)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/direct-messages/threads/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100L))
                .andExpect(jsonPath("$.unreadCount").value(2L));

        verify(directMessageService).getThread(any(UserEntity.class), eq(100L));
    }

    @Test
    @DisplayName("POST /api/v1/direct-messages/threads/{threadId}/messages should send message and return 201 CREATED")
    void sendMessage_ShouldReturn201() throws Exception {
        setupSecurityContext();

        DirectMessageResponseDto response = DirectMessageResponseDto.builder()
                .id(500L)
                .threadId(100L)
                .content("Hello World!")
                .build();

        when(directMessageService.sendMessage(any(UserEntity.class), eq(100L), any(CreateDirectMessageRequestDto.class)))
                .thenReturn(response);

        String requestJson = """
                {
                    "content": "Hello World!"
                }
                """;

        mockMvc.perform(post("/api/v1/direct-messages/threads/100/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(500L))
                .andExpect(jsonPath("$.content").value("Hello World!"));

        verify(directMessageService).sendMessage(any(UserEntity.class), eq(100L), any(CreateDirectMessageRequestDto.class));
    }

    @Test
    @DisplayName("POST /api/v1/direct-messages/threads/{threadId}/read should mark thread as read and return 200 OK")
    void markThreadAsRead_ShouldReturn200() throws Exception {
        setupSecurityContext();

        doNothing().when(directMessageService).markThreadAsRead(any(UserEntity.class), eq(100L));

        mockMvc.perform(post("/api/v1/direct-messages/threads/100/read"))
                .andExpect(status().isOk());

        verify(directMessageService).markThreadAsRead(any(UserEntity.class), eq(100L));
    }

    @Test
    @DisplayName("PUT /api/v1/direct-messages/messages/{messageId} should update message and return 200 OK")
    void updateMessage_ShouldReturn200() throws Exception {
        setupSecurityContext();

        DirectMessageResponseDto response = DirectMessageResponseDto.builder()
                .id(500L)
                .content("Updated content")
                .edited(true)
                .build();

        when(directMessageService.updateMessage(any(UserEntity.class), eq(500L), any(UpdateDirectMessageRequestDto.class)))
                .thenReturn(response);

        String requestJson = """
                {
                    "content": "Updated content"
                }
                """;

        mockMvc.perform(put("/api/v1/direct-messages/messages/500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(500L))
                .andExpect(jsonPath("$.content").value("Updated content"))
                .andExpect(jsonPath("$.edited").value(true));

        verify(directMessageService).updateMessage(any(UserEntity.class), eq(500L), any(UpdateDirectMessageRequestDto.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/direct-messages/messages/{messageId} should delete message and return 204 NO_CONTENT")
    void deleteMessage_ShouldReturn204() throws Exception {
        setupSecurityContext();

        doNothing().when(directMessageService).deleteMessage(any(UserEntity.class), eq(500L));

        mockMvc.perform(delete("/api/v1/direct-messages/messages/500"))
                .andExpect(status().isNoContent());

        verify(directMessageService).deleteMessage(any(UserEntity.class), eq(500L));
    }

    @Test
    @DisplayName("GET /api/v1/direct-messages/privacy should return 200 OK")
    void getPrivacySettings_ShouldReturn200() throws Exception {
        setupSecurityContext();

        UserMessagePrivacySettingsDto response = UserMessagePrivacySettingsDto.builder()
                .allowInternalDms(true)
                .allowExternalDms(true)
                .build();

        when(directMessageService.getPrivacySettings(any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/direct-messages/privacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowInternalDms").value(true))
                .andExpect(jsonPath("$.allowExternalDms").value(true));

        verify(directMessageService).getPrivacySettings(any(UserEntity.class));
    }

    @Test
    @DisplayName("PUT /api/v1/direct-messages/privacy should update privacy settings and return 200 OK")
    void updatePrivacySettings_ShouldReturn200() throws Exception {
        setupSecurityContext();

        UserMessagePrivacySettingsDto response = UserMessagePrivacySettingsDto.builder()
                .allowInternalDms(true)
                .allowExternalDms(false)
                .build();

        when(directMessageService.updatePrivacySettings(any(UserEntity.class), any(UserMessagePrivacySettingsDto.class)))
                .thenReturn(response);

        String requestJson = """
                {
                    "allowInternalDms": true,
                    "allowExternalDms": false,
                    "readReceiptsEnabled": true
                }
                """;

        mockMvc.perform(put("/api/v1/direct-messages/privacy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowInternalDms").value(true))
                .andExpect(jsonPath("$.allowExternalDms").value(false));

        verify(directMessageService).updatePrivacySettings(any(UserEntity.class), any(UserMessagePrivacySettingsDto.class));
    }

    @Test
    @DisplayName("POST /api/v1/direct-messages/blocks/{targetUserId} should block user and return 201 CREATED")
    void blockUser_ShouldReturn201() throws Exception {
        setupSecurityContext();

        UserBlockResponseDto response = UserBlockResponseDto.builder()
                .id(10L)
                .blockedUser(ParticipantSummaryDto.builder().id("usr-target").email("target@example.com").build())
                .build();

        when(directMessageService.blockUser(any(UserEntity.class), eq("usr-target")))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/direct-messages/blocks/usr-target"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.blockedUser.id").value("usr-target"));

        verify(directMessageService).blockUser(any(UserEntity.class), eq("usr-target"));
    }

    @Test
    @DisplayName("DELETE /api/v1/direct-messages/blocks/{targetUserId} should unblock user and return 204 NO_CONTENT")
    void unblockUser_ShouldReturn204() throws Exception {
        setupSecurityContext();

        doNothing().when(directMessageService).unblockUser(any(UserEntity.class), eq("usr-target"));

        mockMvc.perform(delete("/api/v1/direct-messages/blocks/usr-target"))
                .andExpect(status().isNoContent());

        verify(directMessageService).unblockUser(any(UserEntity.class), eq("usr-target"));
    }

    @Test
    @DisplayName("GET /api/v1/direct-messages/blocks should return list of blocked users and 200 OK")
    void getBlockedUsers_ShouldReturn200() throws Exception {
        setupSecurityContext();

        UserBlockResponseDto response = UserBlockResponseDto.builder()
                .id(10L)
                .blockedUser(ParticipantSummaryDto.builder().id("usr-target").email("target@example.com").build())
                .build();

        when(directMessageService.getBlockedUsers(any(UserEntity.class)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/direct-messages/blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].blockedUser.id").value("usr-target"));

        verify(directMessageService).getBlockedUsers(any(UserEntity.class));
    }
}
