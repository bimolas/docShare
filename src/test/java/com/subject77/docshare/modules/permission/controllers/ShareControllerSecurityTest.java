package com.subject77.docshare.modules.permission.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.WebMvcConfig;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.config.security.SecurityConfig;
import com.subject77.docshare.modules.document.dto.DownloadUrlResponse;
import com.subject77.docshare.modules.permission.dto.ShareLinkRequestDto;
import com.subject77.docshare.modules.permission.dto.ShareLinkResponseDto;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.services.ShareService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.services.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShareController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class})
class ShareControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ShareService shareService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UserEntity createTestUser() {
        return UserEntity.builder()
                .id("usr-123")
                .email("caller@docshare.com")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    @DisplayName("Unauthenticated caller is rejected with 401 when creating a share link")
    void createShareLink_Unauthenticated_Rejected() throws Exception {
        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .documentId(10L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        mockMvc.perform(post("/api/v1/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated caller can reach create-share endpoint and passes to service layer")
    void createShareLink_Authenticated_Success() throws Exception {
        UserEntity currentUser = createTestUser();
        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(currentUser);

        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .documentId(10L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        ShareLinkResponseDto response = ShareLinkResponseDto.builder()
                .permissionId(1L)
                .shareLinkToken("tok-abc-123")
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(shareService.createShareLink(any(ShareLinkRequestDto.class), eq(currentUser))).thenReturn(response);

        mockMvc.perform(post("/api/v1/shares")
                        .with(jwt().jwt(j -> j.subject("usr-123").claim("email", "caller@docshare.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("tok-abc-123"));

        verify(shareService).createShareLink(any(ShareLinkRequestDto.class), eq(currentUser));
    }

    @Test
    @DisplayName("Authenticated caller reaches service-level authorization and handles service AccessDeniedException")
    void createShareLink_ServiceAccessDenied_ReturnsForbidden() throws Exception {
        UserEntity currentUser = createTestUser();
        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(currentUser);

        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .documentId(10L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(shareService.createShareLink(any(ShareLinkRequestDto.class), eq(currentUser)))
                .thenThrow(new AccessDeniedException("User does not have permission to share this document"));

        mockMvc.perform(post("/api/v1/shares")
                        .with(jwt().jwt(j -> j.subject("usr-123").claim("email", "caller@docshare.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Public share-info endpoint remains accessible without authentication")
    void getShareLinkInfo_Unauthenticated_Accessible() throws Exception {
        ShareLinkResponseDto response = ShareLinkResponseDto.builder()
                .permissionId(1L)
                .shareLinkToken("public-token")
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(shareService.getShareLinkInfo("public-token")).thenReturn(response);

        mockMvc.perform(get("/api/v1/shares/public-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("public-token"));
    }

    @Test
    @DisplayName("Public share-access endpoint remains accessible without authentication")
    void accessSharedResource_Unauthenticated_Accessible() throws Exception {
        when(shareService.accessSharedResource(eq("public-token"), any(), any(), any()))
                .thenReturn(Collections.singletonMap("access", "granted"));

        mockMvc.perform(post("/api/v1/shares/public-token/access"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Public share-download endpoint remains accessible without authentication")
    void downloadSharedDocument_Unauthenticated_Accessible() throws Exception {
        DownloadUrlResponse downloadResponse = DownloadUrlResponse.builder()
                .downloadUrl("https://s3.amazonaws.com/docshare/test.pdf")
                .fileName("test.pdf")
                .documentId(10L)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        when(shareService.downloadSharedDocument(eq("public-token"), any(), any(), any()))
                .thenReturn(downloadResponse);

        mockMvc.perform(get("/api/v1/shares/public-token/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("test.pdf"));
    }

    @Test
    @DisplayName("Revoke share link requires authentication: unauthenticated call is rejected")
    void revokeShareLink_Unauthenticated_Rejected() throws Exception {
        mockMvc.perform(delete("/api/v1/shares/tok-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Revoke share link succeeds when authenticated")
    void revokeShareLink_Authenticated_Success() throws Exception {
        UserEntity currentUser = createTestUser();
        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(currentUser);

        mockMvc.perform(delete("/api/v1/shares/tok-123")
                        .with(jwt().jwt(j -> j.subject("usr-123").claim("email", "caller@docshare.com"))))
                .andExpect(status().isNoContent());

        verify(shareService).revokeShareLink(eq("tok-123"), eq(currentUser));
    }

    @Test
    @DisplayName("Exclude resource from share requires authentication: unauthenticated call is rejected")
    void excludeResource_Unauthenticated_Rejected() throws Exception {
        mockMvc.perform(post("/api/v1/shares/tok-123/exclude")
                        .param("documentId", "55"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Exclude resource from share succeeds when authenticated")
    void excludeResource_Authenticated_Success() throws Exception {
        UserEntity currentUser = createTestUser();
        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(currentUser);

        ShareLinkResponseDto response = ShareLinkResponseDto.builder()
                .permissionId(1L)
                .shareLinkToken("tok-123")
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(shareService.excludeResourceFromShare(eq("tok-123"), eq(55L), any(), eq(currentUser)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/shares/tok-123/exclude")
                        .with(jwt().jwt(j -> j.subject("usr-123").claim("email", "caller@docshare.com")))
                        .param("documentId", "55"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok-123"));

        verify(shareService).excludeResourceFromShare(eq("tok-123"), eq(55L), any(), eq(currentUser));
    }
}
