package com.subject77.docshare.modules.notification.controllers;

import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.modules.notification.dto.NotificationResponseDto;
import com.subject77.docshare.modules.notification.dto.UnreadCountResponseDto;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationStatus;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private NotificationController notificationController;

    private UserEntity mockUser;
    private static final String USER_ID = "usr-recipient-1";

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

        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setCustomArgumentResolvers(resolver, new PageableHandlerMethodArgumentResolver())
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
                Map.of("sub", USER_ID, "email", "user1@example.com")
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
    }

    @Test
    @DisplayName("GET /api/v1/notifications should return page of notifications and 200 OK")
    void getUserNotifications_ShouldReturn200() throws Exception {
        setupSecurityContext();

        NotificationResponseDto dto = NotificationResponseDto.builder()
                .id(1L)
                .type(NotificationType.PERMISSION_GRANTED)
                .title("Access Granted")
                .body("You have new permission")
                .status(NotificationStatus.UNREAD)
                .sourceType(NotificationSourceType.PERMISSION)
                .createdAt(Instant.now())
                .build();

        when(notificationService.getUserNotifications(any(UserEntity.class), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].title").value("Access Granted"));

        verify(notificationService).getUserNotifications(any(UserEntity.class), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/notifications with category param should filter by category")
    void getUserNotifications_WithCategory_ShouldPassCategory() throws Exception {
        setupSecurityContext();

        when(notificationService.getUserNotifications(any(UserEntity.class), eq(NotificationStatus.UNREAD), isNull(), eq(com.subject77.docshare.modules.notification.entities.NotificationCategory.INVITATION), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("status", "UNREAD")
                        .param("category", "INVITATION"))
                .andExpect(status().isOk());

        verify(notificationService).getUserNotifications(any(UserEntity.class), eq(NotificationStatus.UNREAD), isNull(), eq(com.subject77.docshare.modules.notification.entities.NotificationCategory.INVITATION), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/unread-count should return count and 200 OK")
    void getUnreadCount_ShouldReturn200() throws Exception {
        setupSecurityContext();

        UnreadCountResponseDto dto = UnreadCountResponseDto.builder().unreadCount(3L).build();
        when(notificationService.getUnreadCount(any(UserEntity.class))).thenReturn(dto);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3L));

        verify(notificationService).getUnreadCount(any(UserEntity.class));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read should mark notification read and return 200 OK")
    void markAsRead_ShouldReturn200() throws Exception {
        setupSecurityContext();

        NotificationResponseDto dto = NotificationResponseDto.builder()
                .id(1L)
                .status(NotificationStatus.READ)
                .build();
        when(notificationService.markAsRead(eq(1L), any(UserEntity.class))).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/notifications/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("READ"));

        verify(notificationService).markAsRead(eq(1L), any(UserEntity.class));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/read-all should mark all as read and return 200 OK")
    void markAllAsRead_ShouldReturn200() throws Exception {
        setupSecurityContext();

        doNothing().when(notificationService).markAllAsRead(any(UserEntity.class));

        mockMvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isOk());

        verify(notificationService).markAllAsRead(any(UserEntity.class));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/archive should archive notification and return 200 OK")
    void archiveNotification_ShouldReturn200() throws Exception {
        setupSecurityContext();

        NotificationResponseDto dto = NotificationResponseDto.builder()
                .id(1L)
                .status(NotificationStatus.ARCHIVED)
                .build();
        when(notificationService.archiveNotification(eq(1L), any(UserEntity.class))).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/notifications/1/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        verify(notificationService).archiveNotification(eq(1L), any(UserEntity.class));
    }

    @Test
    @DisplayName("DELETE /api/v1/notifications/{id} should delete notification and return 24 NO_CONTENT")
    void deleteNotification_ShouldReturn204() throws Exception {
        setupSecurityContext();

        doNothing().when(notificationService).deleteNotification(eq(1L), any(UserEntity.class));

        mockMvc.perform(delete("/api/v1/notifications/1"))
                .andExpect(status().isNoContent());

        verify(notificationService).deleteNotification(eq(1L), any(UserEntity.class));
    }
}
