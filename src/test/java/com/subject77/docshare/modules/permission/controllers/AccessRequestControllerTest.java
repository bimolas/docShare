package com.subject77.docshare.modules.permission.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.modules.permission.dto.AccessRequestActionDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestResponseDto;
import com.subject77.docshare.modules.permission.entities.AccessRequestStatus;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.services.AccessRequestService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccessRequestControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AccessRequestService accessRequestService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private AccessRequestController accessRequestController;

    private UserEntity mockUser;
    private static final String USER_ID = "usr-test-1";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUser = UserEntity.builder()
                .id(USER_ID)
                .email("user1@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .role(Role.ROLE_USER)
                .build();

        lenient().when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(accessRequestController)
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
    @DisplayName("POST /api/v1/access-requests should create access request and return 201 CREATED")
    void createAccessRequest_ShouldReturn201() throws Exception {
        setupSecurityContext();

        AccessRequestCreateDto createDto = AccessRequestCreateDto.builder()
                .documentId(100L)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .reason("Need for research project")
                .build();

        AccessRequestResponseDto responseDto = AccessRequestResponseDto.builder()
                .id(1L)
                .documentId(100L)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .reason("Need for research project")
                .requesterUserEmail("user1@example.com")
                .createdAt(Instant.now())
                .build();

        when(accessRequestService.createAccessRequest(any(AccessRequestCreateDto.class), any(UserEntity.class)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/access-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedPermissionLevel").value("VIEW"))
                .andExpect(jsonPath("$.requesterUserEmail").value("user1@example.com"));

        verify(accessRequestService).createAccessRequest(any(AccessRequestCreateDto.class), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/access-requests with missing requested permission level should return 400 BAD REQUEST")
    void createAccessRequest_InvalidBody_ShouldReturn400() throws Exception {
        AccessRequestCreateDto invalidDto = AccessRequestCreateDto.builder()
                .documentId(100L)
                // missing requestedPermissionLevel
                .build();

        mockMvc.perform(post("/api/v1/access-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/access-requests/my should return page of my access requests and 200 OK")
    void getMyAccessRequests_ShouldReturn200() throws Exception {
        setupSecurityContext();

        AccessRequestResponseDto responseDto = AccessRequestResponseDto.builder()
                .id(1L)
                .documentId(100L)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestService.getMyAccessRequests(any(UserEntity.class), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(responseDto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/access-requests/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        verify(accessRequestService).getMyAccessRequests(any(UserEntity.class), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/access-requests/my with status filter should pass filter and return 200 OK")
    void getMyAccessRequests_WithStatusFilter_ShouldReturn200() throws Exception {
        setupSecurityContext();

        AccessRequestResponseDto responseDto = AccessRequestResponseDto.builder()
                .id(1L)
                .documentId(100L)
                .status(AccessRequestStatus.APPROVED)
                .build();

        when(accessRequestService.getMyAccessRequests(any(UserEntity.class), eq(AccessRequestStatus.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(responseDto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/access-requests/my")
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"));

        verify(accessRequestService).getMyAccessRequests(any(UserEntity.class), eq(AccessRequestStatus.APPROVED), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/access-requests/pending-reviews should return page of pending reviews and 200 OK")
    void getPendingReviews_ShouldReturn200() throws Exception {
        setupSecurityContext();

        AccessRequestResponseDto responseDto = AccessRequestResponseDto.builder()
                .id(2L)
                .folderId(200L)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestService.getPendingReviews(any(UserEntity.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(responseDto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/access-requests/pending-reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2L))
                .andExpect(jsonPath("$.content[0].folderId").value(200L));

        verify(accessRequestService).getPendingReviews(any(UserEntity.class), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/access-requests/reviews should return page of reviews and 200 OK")
    void getReviewerAccessRequests_ShouldReturn200() throws Exception {
        setupSecurityContext();

        AccessRequestResponseDto responseDto = AccessRequestResponseDto.builder()
                .id(3L)
                .documentId(300L)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestService.getReviewerAccessRequests(any(UserEntity.class), eq(AccessRequestStatus.PENDING), isNull(), isNull(), eq("test"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(responseDto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/access-requests/reviews")
                        .param("status", "PENDING")
                        .param("search", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3L))
                .andExpect(jsonPath("$.content[0].documentId").value(300L));

        verify(accessRequestService).getReviewerAccessRequests(any(UserEntity.class), eq(AccessRequestStatus.PENDING), isNull(), isNull(), eq("test"), any(Pageable.class));
    }

    @Test
    @DisplayName("GET /api/v1/access-requests/{id} should return access request details and 200 OK")
    void getAccessRequestById_ShouldReturn200() throws Exception {
        setupSecurityContext();

        AccessRequestResponseDto responseDto = AccessRequestResponseDto.builder()
                .id(10L)
                .documentId(100L)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestService.getAccessRequestById(eq(10L), any(UserEntity.class)))
                .thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/access-requests/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.documentId").value(100L));

        verify(accessRequestService).getAccessRequestById(eq(10L), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/access-requests/{id}/approve should approve request and return 200 OK")
    void approveAccessRequest_ShouldReturn200() throws Exception {
        setupSecurityContext();

        AccessRequestActionDto actionDto = AccessRequestActionDto.builder()
                .reviewerNote("Looks good")
                .build();

        AccessRequestResponseDto responseDto = AccessRequestResponseDto.builder()
                .id(10L)
                .status(AccessRequestStatus.APPROVED)
                .reviewerNote("Looks good")
                .processedByUserEmail("user1@example.com")
                .build();

        when(accessRequestService.approveAccessRequest(eq(10L), any(AccessRequestActionDto.class), any(UserEntity.class)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/access-requests/10/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actionDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewerNote").value("Looks good"));

        verify(accessRequestService).approveAccessRequest(eq(10L), any(AccessRequestActionDto.class), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/access-requests/{id}/deny should deny request and return 200 OK")
    void denyAccessRequest_ShouldReturn200() throws Exception {
        setupSecurityContext();

        AccessRequestActionDto actionDto = AccessRequestActionDto.builder()
                .reviewerNote("Not authorized")
                .build();

        AccessRequestResponseDto responseDto = AccessRequestResponseDto.builder()
                .id(10L)
                .status(AccessRequestStatus.DENIED)
                .reviewerNote("Not authorized")
                .processedByUserEmail("user1@example.com")
                .build();

        when(accessRequestService.denyAccessRequest(eq(10L), any(AccessRequestActionDto.class), any(UserEntity.class)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/access-requests/10/deny")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actionDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.status").value("DENIED"))
                .andExpect(jsonPath("$.reviewerNote").value("Not authorized"));

        verify(accessRequestService).denyAccessRequest(eq(10L), any(AccessRequestActionDto.class), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/access-requests/{id}/cancel should cancel request and return 200 OK")
    void cancelAccessRequest_ShouldReturn200() throws Exception {
        setupSecurityContext();

        AccessRequestResponseDto responseDto = AccessRequestResponseDto.builder()
                .id(10L)
                .status(AccessRequestStatus.CANCELLED)
                .build();

        when(accessRequestService.cancelAccessRequest(eq(10L), any(UserEntity.class)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/access-requests/10/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(accessRequestService).cancelAccessRequest(eq(10L), any(UserEntity.class));
    }
}
