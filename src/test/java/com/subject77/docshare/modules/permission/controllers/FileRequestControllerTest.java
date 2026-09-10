package com.subject77.docshare.modules.permission.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.modules.document.dto.ConfirmUploadRequest;
import com.subject77.docshare.modules.document.dto.DocumentResponse;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.dto.InitiateUploadRequest;
import com.subject77.docshare.modules.document.dto.InitiateUploadResponse;
import com.subject77.docshare.modules.permission.dto.FileRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.FileRequestResponseDto;
import com.subject77.docshare.modules.permission.entities.FileRequestStatus;
import com.subject77.docshare.modules.permission.services.FileRequestService;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileRequestControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private FileRequestService fileRequestService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private FileRequestController fileRequestController;

    private UserEntity mockUser;
    private static final String USER_ID = "usr-requester-100";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUser = UserEntity.builder()
                .id(USER_ID)
                .email("requester@org.com")
                .firstName("Requester")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        lenient().when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(fileRequestController)
                .setCustomArgumentResolvers(resolver)
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
                Map.of("sub", USER_ID, "email", "requester@org.com")
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
    }

    @Test
    @DisplayName("POST /api/v1/file-requests should create file request and return 201 CREATED")
    void createFileRequest_ShouldReturn201() throws Exception {
        setupSecurityContext();

        FileRequestCreateDto requestDto = FileRequestCreateDto.builder()
                .destinationFolderId(100L)
                .recipientEmail("recipient@org.com")
                .title("Tax Documents")
                .note("Please upload W2")
                .build();

        FileRequestResponseDto responseDto = FileRequestResponseDto.builder()
                .id(1L)
                .destinationFolderId(100L)
                .destinationFolderName("Finances")
                .requesterId(USER_ID)
                .requesterEmail("requester@org.com")
                .recipientEmail("recipient@org.com")
                .title("Tax Documents")
                .note("Please upload W2")
                .status(FileRequestStatus.PENDING)
                .build();

        when(fileRequestService.createFileRequest(any(FileRequestCreateDto.class), any(UserEntity.class)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/file-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Tax Documents"))
                .andExpect(jsonPath("$.recipientEmail").value("recipient@org.com"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(fileRequestService).createFileRequest(any(FileRequestCreateDto.class), any(UserEntity.class));
    }

    @Test
    @DisplayName("GET /api/v1/file-requests/mine should return requests created by current user")
    void getRequestsMine_ShouldReturn200() throws Exception {
        setupSecurityContext();

        FileRequestResponseDto r1 = FileRequestResponseDto.builder().id(1L).title("Req 1").build();
        FileRequestResponseDto r2 = FileRequestResponseDto.builder().id(2L).title("Req 2").build();

        when(fileRequestService.getRequestsMine(any(UserEntity.class))).thenReturn(List.of(r1, r2));

        mockMvc.perform(get("/api/v1/file-requests/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[1].id").value(2L));

        verify(fileRequestService).getRequestsMine(any(UserEntity.class));
    }

    @Test
    @DisplayName("GET /api/v1/file-requests/received should return requests received by current user")
    void getRequestsReceived_ShouldReturn200() throws Exception {
        setupSecurityContext();

        FileRequestResponseDto r1 = FileRequestResponseDto.builder().id(3L).title("Rec 1").build();

        when(fileRequestService.getRequestsReceived(any(UserEntity.class))).thenReturn(List.of(r1));

        mockMvc.perform(get("/api/v1/file-requests/received"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(3L));

        verify(fileRequestService).getRequestsReceived(any(UserEntity.class));
    }

    @Test
    @DisplayName("GET /api/v1/file-requests/{id} should return file request details")
    void getFileRequestById_ShouldReturn200() throws Exception {
        setupSecurityContext();

        FileRequestResponseDto responseDto = FileRequestResponseDto.builder()
                .id(1L)
                .title("Tax Documents")
                .status(FileRequestStatus.PENDING)
                .build();

        when(fileRequestService.getFileRequestById(eq(1L), any(UserEntity.class)))
                .thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/file-requests/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Tax Documents"));

        verify(fileRequestService).getFileRequestById(eq(1L), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/file-requests/{id}/uploads/initiate should initiate upload")
    void initiateUpload_ShouldReturn200() throws Exception {
        setupSecurityContext();

        InitiateUploadRequest request = InitiateUploadRequest.builder()
                .fileName("w2.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .fileHash("hash123")
                .build();

        InitiateUploadResponse response = InitiateUploadResponse.builder()
                .documentId(500L)
                .objectKey("temp/w2.pdf")
                .presignedUploadUrl("http://s3.local/upload")
                .status(DocumentStatus.PENDING_UPLOAD)
                .build();

        when(fileRequestService.initiateUpload(eq(1L), any(InitiateUploadRequest.class), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/file-requests/1/uploads/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(500L))
                .andExpect(jsonPath("$.presignedUploadUrl").value("http://s3.local/upload"));

        verify(fileRequestService).initiateUpload(eq(1L), any(InitiateUploadRequest.class), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/file-requests/{id}/uploads/confirm should confirm upload")
    void confirmUpload_ShouldReturn200() throws Exception {
        setupSecurityContext();

        ConfirmUploadRequest request = ConfirmUploadRequest.builder()
                .documentId(500L)
                .clientHash("etag-abc")
                .build();

        DocumentResponse response = DocumentResponse.builder()
                .id(500L)
                .fileName("w2.pdf")
                .fileSize(1024L)
                .contentType("application/pdf")
                .status(DocumentStatus.ACTIVE)
                .build();

        when(fileRequestService.confirmUpload(eq(1L), any(ConfirmUploadRequest.class), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/file-requests/1/uploads/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(500L))
                .andExpect(jsonPath("$.fileName").value("w2.pdf"));

        verify(fileRequestService).confirmUpload(eq(1L), any(ConfirmUploadRequest.class), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/file-requests/{id}/complete should complete file request")
    void completeFileRequest_ShouldReturn200() throws Exception {
        setupSecurityContext();

        FileRequestResponseDto responseDto = FileRequestResponseDto.builder()
                .id(1L)
                .status(FileRequestStatus.FULFILLED)
                .build();

        when(fileRequestService.completeFileRequest(eq(1L), any(UserEntity.class)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/file-requests/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("FULFILLED"));

        verify(fileRequestService).completeFileRequest(eq(1L), any(UserEntity.class));
    }

    @Test
    @DisplayName("POST /api/v1/file-requests/{id}/cancel should cancel file request")
    void cancelFileRequest_ShouldReturn200() throws Exception {
        setupSecurityContext();

        FileRequestResponseDto responseDto = FileRequestResponseDto.builder()
                .id(1L)
                .status(FileRequestStatus.CANCELLED)
                .build();

        when(fileRequestService.cancelFileRequest(eq(1L), any(UserEntity.class)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/file-requests/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(fileRequestService).cancelFileRequest(eq(1L), any(UserEntity.class));
    }
}
