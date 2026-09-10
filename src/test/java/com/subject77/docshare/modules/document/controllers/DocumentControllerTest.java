package com.subject77.docshare.modules.document.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.modules.document.dto.*;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.services.DocumentService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DocumentService documentService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private DocumentController documentController;

    private ObjectMapper objectMapper;
    private UserEntity mockUser;

    private static final String USER_ID = "7128fe1a-90d5-43ea-b3c7-1ffe727e8a68";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        mockUser = UserEntity.builder()
                .id(USER_ID)
                .email("miko@gmail.com")
                .firstName("Miko")
                .lastName("Dev")
                .role(Role.ROLE_USER)
                .build();

        when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(documentController)
                .setCustomArgumentResolvers(resolver)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/documents/upload/initiate should return 201 Created and InitiateUploadResponse")
    void initiateUpload_ShouldReturn201() throws Exception {
        setupSecurityContext();

        InitiateUploadRequest request = InitiateUploadRequest.builder()
                .fileName("test-file.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .fileHash("hash123")
                .build();

        InitiateUploadResponse serviceResponse = InitiateUploadResponse.builder()
                .documentId(1L)
                .presignedUploadUrl("https://s3.amazonaws.com/presigned-upload")
                .s3Bucket("docshare-bucket")
                .objectKey("users/" + USER_ID + "/uuid-test-file.pdf")
                .status(DocumentStatus.PENDING_UPLOAD)
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        when(documentService.initiateUpload(any(InitiateUploadRequest.class), any(UserEntity.class)))
                .thenReturn(serviceResponse);

        mockMvc.perform(post("/api/v1/documents/upload/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(1L))
                .andExpect(jsonPath("$.presignedUploadUrl").value("https://s3.amazonaws.com/presigned-upload"))
                .andExpect(jsonPath("$.status").value("PENDING_UPLOAD"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    @DisplayName("POST /api/v1/documents/{id}/upload/confirm should return 200 OK and DocumentResponse")
    void confirmUpload_ShouldReturn200() throws Exception {
        setupSecurityContext();

        ConfirmUploadRequest request = ConfirmUploadRequest.builder()
                .clientHash("hash123")
                .build();

        DocumentResponse response = DocumentResponse.builder()
                .id(1L)
                .fileName("test-file.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .status(DocumentStatus.ACTIVE)
                .uploadedByUserId(USER_ID)
                .uploadedByUserEmail("miko@gmail.com")
                .build();

        when(documentService.confirmUpload(eq(1L), any(), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/documents/1/upload/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.fileName").value("test-file.pdf"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/download should return 200 OK and DownloadUrlResponse")
    void getDownloadUrl_ShouldReturn200() throws Exception {
        setupSecurityContext();

        DownloadUrlResponse response = DownloadUrlResponse.builder()
                .documentId(1L)
                .downloadUrl("https://s3.amazonaws.com/presigned-download")
                .fileName("test-file.pdf")
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        when(documentService.generateDownloadUrl(eq(1L), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/documents/1/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(1L))
                .andExpect(jsonPath("$.downloadUrl").value("https://s3.amazonaws.com/presigned-download"))
                .andExpect(jsonPath("$.fileName").value("test-file.pdf"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id}/preview should return 200 OK and PreviewUrlResponse")
    void getPreviewUrl_ShouldReturn200() throws Exception {
        setupSecurityContext();

        PreviewUrlResponse response = PreviewUrlResponse.builder()
                .documentId(1L)
                .previewUrl("https://s3.amazonaws.com/presigned-preview")
                .fileName("test-file.pdf")
                .contentType("application/pdf")
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        when(documentService.generatePreviewUrl(eq(1L), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/documents/1/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(1L))
                .andExpect(jsonPath("$.previewUrl").value("https://s3.amazonaws.com/presigned-preview"))
                .andExpect(jsonPath("$.fileName").value("test-file.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"));
    }

    @Test
    @DisplayName("GET /api/v1/documents/{id} should return 200 OK and DocumentResponse details")
    void getDocumentDetails_ShouldReturn200() throws Exception {
        setupSecurityContext();

        DocumentResponse response = DocumentResponse.builder()
                .id(1L)
                .fileName("test-file.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .status(DocumentStatus.ACTIVE)
                .uploadedByUserId(USER_ID)
                .uploadedByUserEmail("miko@gmail.com")
                .build();

        when(documentService.getDocumentDetails(eq(1L), any(UserEntity.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.fileName").value("test-file.pdf"))
                .andExpect(jsonPath("$.uploadedByUserId").value(USER_ID));
    }

    @Test
    @DisplayName("DELETE /api/v1/documents/{id} should return 204 No Content")
    void deleteDocument_ShouldReturn204() throws Exception {
        setupSecurityContext();

        doNothing().when(documentService).deleteDocument(eq(1L), any(UserEntity.class));

        mockMvc.perform(delete("/api/v1/documents/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/documents/{id}/versions/{versionNumber}/restore should return 200 OK and DocumentResponse")
    void restoreVersion_ShouldReturn200() throws Exception {
        setupSecurityContext();

        RestoreVersionRequest request = RestoreVersionRequest.builder()
                .reason("Reverting to v1")
                .build();

        DocumentResponse response = DocumentResponse.builder()
                .id(1L)
                .fileName("test-file.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .status(DocumentStatus.ACTIVE)
                .uploadedByUserId(USER_ID)
                .uploadedByUserEmail("miko@gmail.com")
                .build();

        when(documentService.restoreVersion(eq(1L), eq(1), any(RestoreVersionRequest.class), any(UserEntity.class), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/documents/1/versions/1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("User-Agent", "JUnit-Agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.fileName").value("test-file.pdf"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
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
                Map.of("sub", USER_ID, "email", "miko@gmail.com")
        );
    }
}
