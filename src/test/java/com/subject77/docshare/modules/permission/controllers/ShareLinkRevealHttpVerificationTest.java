package com.subject77.docshare.modules.permission.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.WebMvcConfig;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.config.security.SecurityConfig;
import com.subject77.docshare.exceptions.GlobalExceptionHandler;
import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.permission.dto.EffectiveAccessAuditDto;
import com.subject77.docshare.modules.permission.dto.ShareLinkResponseDto;
import com.subject77.docshare.modules.permission.dto.ShareLinkRevealResponseDto;
import com.subject77.docshare.modules.permission.entities.AuthorityTier;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.permission.services.PermissionService;
import com.subject77.docshare.modules.permission.services.ShareService;
import com.subject77.docshare.modules.permission.services.ShareServiceImpl;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ShareController.class, PermissionController.class})
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentUserArgumentResolver.class, GlobalExceptionHandler.class})
class ShareLinkRevealHttpVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShareService shareService;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UserEntity creatorUser;
    private UserEntity orgAdminUser;
    private UserEntity workspaceManagerUser;
    private UserEntity collaborationManagerUser;
    private UserEntity superAdminUser;

    @BeforeEach
    void setUp() {
        creatorUser = UserEntity.builder()
                .id("usr-creator-1")
                .email("creator@docshare.com")
                .role(Role.ROLE_USER)
                .build();

        orgAdminUser = UserEntity.builder()
                .id("usr-org-admin-2")
                .email("orgadmin@docshare.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        workspaceManagerUser = UserEntity.builder()
                .id("usr-wm-3")
                .email("wm@docshare.com")
                .role(Role.ROLE_USER)
                .build();

        collaborationManagerUser = UserEntity.builder()
                .id("usr-cm-4")
                .email("cm@docshare.com")
                .role(Role.ROLE_USER)
                .build();

        superAdminUser = UserEntity.builder()
                .id("usr-super-5")
                .email("super@docshare.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();
    }

    @Nested
    @DisplayName("1 & 2. POST /api/v1/shares/links/{permissionId}/reveal HTTP Security & Authorization Boundary")
    class RevealEndpointHttpVerification {

        @Test
        @DisplayName("Authenticated original creator -> 200 OK + ShareLinkRevealResponseDto")
        void reveal_AuthenticatedCreator_Returns200AndRevealDto() throws Exception {
            when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(creatorUser);

            Instant expiresAt = Instant.parse("2026-12-31T23:59:59Z");
            ShareLinkRevealResponseDto responseDto = ShareLinkRevealResponseDto.builder()
                    .permissionId(101L)
                    .shareLinkToken("bearer_token_secret_xyz")
                    .shareUrl("/share/bearer_token_secret_xyz")
                    .expiresAt(expiresAt)
                    .build();

            when(shareService.revealShareLink(eq(101L), eq(creatorUser))).thenReturn(responseDto);

            mockMvc.perform(post("/api/v1/shares/links/101/reveal")
                            .with(jwt().jwt(j -> j.subject("usr-creator-1").claim("email", "creator@docshare.com"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.permissionId").value(101L))
                    .andExpect(jsonPath("$.shareLinkToken").value("bearer_token_secret_xyz"))
                    .andExpect(jsonPath("$.shareUrl").value("/share/bearer_token_secret_xyz"))
                    .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
                    // Verify no extraneous internal fields leak
                    .andExpect(jsonPath("$.recordHash").doesNotExist())
                    .andExpect(jsonPath("$.blockchainStatus").doesNotExist())
                    .andExpect(jsonPath("$.grantedBy").doesNotExist())
                    .andExpect(jsonPath("$.document").doesNotExist())
                    .andExpect(jsonPath("$.folder").doesNotExist());

            verify(shareService).revealShareLink(101L, creatorUser);
        }

        @Test
        @DisplayName("Creator who no longer has resource access -> 403 Forbidden")
        void reveal_CreatorLostAccess_Returns403Forbidden() throws Exception {
            when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(creatorUser);

            when(shareService.revealShareLink(eq(101L), eq(creatorUser)))
                    .thenThrow(new AccessDeniedException("User no longer has access to this resource."));

            mockMvc.perform(post("/api/v1/shares/links/101/reveal")
                            .with(jwt().jwt(j -> j.subject("usr-creator-1").claim("email", "creator@docshare.com"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").value("User no longer has access to this resource."))
                    .andExpect(jsonPath("$.path").value("/api/v1/shares/links/101/reveal"));
        }

        @Test
        @DisplayName("Non-creator ORG_ADMIN -> 403 Forbidden")
        void reveal_NonCreatorOrgAdmin_Returns403Forbidden() throws Exception {
            when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(orgAdminUser);

            when(shareService.revealShareLink(eq(101L), eq(orgAdminUser)))
                    .thenThrow(new AccessDeniedException("Only the original creator of this share link (or the personal resource owner) has authority to reveal the bearer secret."));

            mockMvc.perform(post("/api/v1/shares/links/101/reveal")
                            .with(jwt().jwt(j -> j.subject("usr-org-admin-2").claim("email", "orgadmin@docshare.com"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").value("Only the original creator of this share link (or the personal resource owner) has authority to reveal the bearer secret."));
        }

        @Test
        @DisplayName("Non-creator WORKSPACE_MANAGER -> 403 Forbidden")
        void reveal_NonCreatorWorkspaceManager_Returns403Forbidden() throws Exception {
            when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(workspaceManagerUser);

            when(shareService.revealShareLink(eq(101L), eq(workspaceManagerUser)))
                    .thenThrow(new AccessDeniedException("Only the original creator of this share link (or the personal resource owner) has authority to reveal the bearer secret."));

            mockMvc.perform(post("/api/v1/shares/links/101/reveal")
                            .with(jwt().jwt(j -> j.subject("usr-wm-3").claim("email", "wm@docshare.com"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Non-creator COLLABORATION_MANAGER -> 403 Forbidden")
        void reveal_NonCreatorCollaborationManager_Returns403Forbidden() throws Exception {
            when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(collaborationManagerUser);

            when(shareService.revealShareLink(eq(101L), eq(collaborationManagerUser)))
                    .thenThrow(new AccessDeniedException("Only the original creator of this share link (or the personal resource owner) has authority to reveal the bearer secret."));

            mockMvc.perform(post("/api/v1/shares/links/101/reveal")
                            .with(jwt().jwt(j -> j.subject("usr-cm-4").claim("email", "cm@docshare.com"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Non-creator SUPER_ADMIN -> 403 Forbidden (No administrative backdoor)")
        void reveal_NonCreatorSuperAdmin_Returns403Forbidden() throws Exception {
            when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(superAdminUser);

            when(shareService.revealShareLink(eq(101L), eq(superAdminUser)))
                    .thenThrow(new AccessDeniedException("Only the original creator of this share link (or the personal resource owner) has authority to reveal the bearer secret."));

            mockMvc.perform(post("/api/v1/shares/links/101/reveal")
                            .with(jwt().jwt(j -> j.subject("usr-super-5").claim("email", "super@docshare.com"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Unauthenticated caller -> 401 Unauthorized")
        void reveal_UnauthenticatedCaller_Returns401Unauthorized() throws Exception {
            mockMvc.perform(post("/api/v1/shares/links/101/reveal"))
                    .andExpect(status().isUnauthorized());

            verifyNoInteractions(shareService);
        }

        @Test
        @DisplayName("Revoked link -> 400 Bad Request")
        void reveal_RevokedLink_Returns400BadRequest() throws Exception {
            when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(creatorUser);

            when(shareService.revealShareLink(eq(101L), eq(creatorUser)))
                    .thenThrow(new IllegalArgumentException("Cannot reveal a revoked share link."));

            mockMvc.perform(post("/api/v1/shares/links/101/reveal")
                            .with(jwt().jwt(j -> j.subject("usr-creator-1").claim("email", "creator@docshare.com"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Cannot reveal a revoked share link."));
        }

        @Test
        @DisplayName("Expired link -> 400 Bad Request")
        void reveal_ExpiredLink_Returns400BadRequest() throws Exception {
            when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(creatorUser);

            when(shareService.revealShareLink(eq(101L), eq(creatorUser)))
                    .thenThrow(new IllegalArgumentException("Cannot reveal an expired share link."));

            mockMvc.perform(post("/api/v1/shares/links/101/reveal")
                            .with(jwt().jwt(j -> j.subject("usr-creator-1").claim("email", "creator@docshare.com"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Cannot reveal an expired share link."));
        }

        @Test
        @DisplayName("Non-share-link permission -> 400 Bad Request")
        void reveal_NonShareLinkPermission_Returns400BadRequest() throws Exception {
            when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(creatorUser);

            when(shareService.revealShareLink(eq(101L), eq(creatorUser)))
                    .thenThrow(new IllegalArgumentException("Permission is not a share link."));

            mockMvc.perform(post("/api/v1/shares/links/101/reveal")
                            .with(jwt().jwt(j -> j.subject("usr-creator-1").claim("email", "creator@docshare.com"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Permission is not a share link."));
        }

        @Test
        @DisplayName("Nonexistent permission ID -> 404 Not Found")
        void reveal_NonexistentPermissionId_Returns404NotFound() throws Exception {
            when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(creatorUser);

            when(shareService.revealShareLink(eq(99999L), eq(creatorUser)))
                    .thenThrow(new ResourceNotFoundException("Share link not found with ID: 99999"));

            mockMvc.perform(post("/api/v1/shares/links/99999/reveal")
                            .with(jwt().jwt(j -> j.subject("usr-creator-1").claim("email", "creator@docshare.com"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Share link not found with ID: 99999"));
        }
    }

    @Nested
    @DisplayName("4. GET /api/v1/permissions/effective HTTP Response Sanitization & canReveal Verification")
    class EffectivePermissionsHttpVerification {

        @Test
        @DisplayName("Effective permissions HTTP response sanitizes shareLinkToken to null and includes canReveal flag")
        void getEffectivePermissions_SanitizesTokenAndExposesCanReveal() throws Exception {
            when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(creatorUser);

            ShareLinkResponseDto sanitizedCreatorLink = ShareLinkResponseDto.builder()
                    .permissionId(501L)
                    .shareLinkToken(null) // STRICT INVARIANT: null in passive responses
                    .createdByUserId("usr-creator-1")
                    .createdByUserEmail("creator@docshare.com")
                    .canReveal(true)
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .active(true)
                    .build();

            ShareLinkResponseDto sanitizedAdminViewLink = ShareLinkResponseDto.builder()
                    .permissionId(502L)
                    .shareLinkToken(null) // STRICT INVARIANT: null in passive responses
                    .createdByUserId("usr-other-creator")
                    .createdByUserEmail("other@docshare.com")
                    .canReveal(false)
                    .permissionLevel(PermissionLevel.DOWNLOAD)
                    .permissionType(PermissionType.ALLOW)
                    .active(true)
                    .build();

            EffectiveAccessAuditDto effectiveAudit = EffectiveAccessAuditDto.builder()
                    .resourceId(200L)
                    .resourceType("FOLDER")
                    .resourceName("Team Folder")
                    .ownerEmail("owner@docshare.com")
                    .activeShareLinks(List.of(sanitizedCreatorLink, sanitizedAdminViewLink))
                    .directPermissions(Collections.emptyList())
                    .inheritedPermissions(Collections.emptyList())
                    .userAccessSummaries(Collections.emptyList())
                    .build();

            when(permissionService.getEffectivePermissions(eq(200L), eq("FOLDER"), eq(creatorUser)))
                    .thenReturn(effectiveAudit);

            mockMvc.perform(get("/api/v1/permissions/effective")
                            .with(jwt().jwt(j -> j.subject("usr-creator-1").claim("email", "creator@docshare.com")))
                            .param("targetId", "200")
                            .param("targetType", "FOLDER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeShareLinks[0].token").doesNotExist())
                    .andExpect(jsonPath("$.activeShareLinks[0].createdByUserId").value("usr-creator-1"))
                    .andExpect(jsonPath("$.activeShareLinks[0].canReveal").value(true))
                    .andExpect(jsonPath("$.activeShareLinks[1].token").doesNotExist())
                    .andExpect(jsonPath("$.activeShareLinks[1].createdByUserId").value("usr-other-creator"))
                    .andExpect(jsonPath("$.activeShareLinks[1].canReveal").value(false));
        }
    }

    @Nested
    @DisplayName("5. Audit Behavior through Real Service Path Verification")
    class AuditBehaviorRealServiceVerification {

        private PermissionRepository mockPermissionRepo;
        private DocumentRepository mockDocRepo;
        private PermissionEvaluatorService mockEvaluator;
        private AuditLogService mockAuditLog;
        private PermissionEventPublisher mockEventPublisher;
        private ShareServiceImpl realShareService;

        private FolderEntity testOrgFolder;
        private PermissionEntity testShareLinkPerm;

        @BeforeEach
        void setUpService() {
            mockPermissionRepo = mock(PermissionRepository.class);
            mockDocRepo = mock(DocumentRepository.class);
            mockEvaluator = mock(PermissionEvaluatorService.class);
            mockAuditLog = mock(AuditLogService.class);
            mockEventPublisher = mock(PermissionEventPublisher.class);

            realShareService = new ShareServiceImpl(
                    mockPermissionRepo,
                    mockAuditLog,
                    mockDocRepo,
                    null, // folderRepo
                    null, // passwordEncoder
                    mockEvaluator,
                    mockEventPublisher,
                    null, // cacheService
                    null, // storageService
                    null  // externalSharingPolicyEvaluator
            );

            OrganizationEntity org = OrganizationEntity.builder()
                    .orgCode("DOC_CORP")
                    .name("Doc Corp")
                    .build();

            testOrgFolder = FolderEntity.builder()
                    .id(300L)
                    .name("AuditFolder")
                    .ownerOrg(org)
                    .build();

            testShareLinkPerm = PermissionEntity.builder()
                    .id(7001L)
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .folder(testOrgFolder)
                    .grantedBy(creatorUser)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .shareLinkToken("raw_secret_bearer_token_super_confidential_999")
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .revoked(false)
                    .recordHash("hash-7001")
                    .build();
        }

        @Test
        @DisplayName("Successful reveal emits exactly one REVEALED_SHARE_LINK audit event without plaintext token")
        void reveal_Success_EmitsAuditEventWithoutPlaintextToken() {
            when(mockPermissionRepo.findById(7001L)).thenReturn(Optional.of(testShareLinkPerm));
            when(mockEvaluator.canViewFolder(creatorUser, testOrgFolder)).thenReturn(true);

            ShareLinkRevealResponseDto res = realShareService.revealShareLink(7001L, creatorUser);

            assertThat(res).isNotNull();
            assertThat(res.getShareLinkToken()).isEqualTo("raw_secret_bearer_token_super_confidential_999");

            ArgumentCaptor<AuditPermissionChangeCommand> captor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
            verify(mockAuditLog, times(1)).logPermissionChange(captor.capture());

            AuditPermissionChangeCommand auditCmd = captor.getValue();
            assertThat(auditCmd.getAction()).isEqualTo("REVEALED_SHARE_LINK");
            assertThat(auditCmd.getTargetType()).isEqualTo("FOLDER");
            assertThat(auditCmd.getTargetId()).isEqualTo(300L);
            assertThat(auditCmd.getActorUserId()).isEqualTo(creatorUser.getId());
            assertThat(auditCmd.getOrgCode()).isEqualTo("DOC_CORP");

            // CRITICAL SECURITY ASSERTION: Plaintext token must NEVER appear anywhere in audit command
            assertThat(auditCmd.toString()).doesNotContain("raw_secret_bearer_token_super_confidential_999");
            assertThat(auditCmd.getTargetFileHash()).isNull();
        }

        @Test
        @DisplayName("Failed reveal attempts (e.g. non-creator admin) NEVER emit REVEALED_SHARE_LINK audit event")
        void reveal_FailedAttempt_DoesNotEmitAuditEvent() {
            when(mockPermissionRepo.findById(7001L)).thenReturn(Optional.of(testShareLinkPerm));

            try {
                realShareService.revealShareLink(7001L, orgAdminUser);
            } catch (AccessDeniedException ignored) {
            }

            verify(mockAuditLog, never()).logPermissionChange(any());
        }
    }
}
