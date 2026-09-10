package com.subject77.docshare.modules.organization.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.modules.organization.dto.MemberAccessSummaryResponseDto;
import com.subject77.docshare.modules.organization.dto.OrganizationMemberDetailResponseDto;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.services.OrganizationService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.services.UserService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private OrganizationMemberController organizationMemberController;

    private UserEntity mockUser;
    private OrganizationMemberDetailResponseDto detailDto;

    private static final String USER_ID = "user-123";
    private static final String TARGET_USER_ID = "target-456";
    private static final String ORG_CODE = "ACME";

    @BeforeEach
    void setUp() {
        mockUser = UserEntity.builder()
                .id(USER_ID)
                .email("admin@acme.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        detailDto = OrganizationMemberDetailResponseDto.builder()
                .userId(TARGET_USER_ID)
                .email("member@acme.com")
                .firstName("Member")
                .lastName("User")
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        org.mockito.Mockito.lenient().when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(organizationMemberController)
                .setCustomArgumentResolvers(resolver)
                .setControllerAdvice(new com.subject77.docshare.exceptions.GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/organizations/{orgCode}/members/{userId} should return member details (canonical)")
    void getOrganizationMemberDetail_CanonicalPath_ShouldReturnDetail() throws Exception {
        Jwt jwt = createJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
        when(organizationService.getOrganizationMemberDetail(eq(ORG_CODE), eq(TARGET_USER_ID), any(UserEntity.class)))
                .thenReturn(detailDto);

        mockMvc.perform(get("/api/v1/organizations/{orgCode}/members/{userId}", ORG_CODE, TARGET_USER_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(TARGET_USER_ID))
                .andExpect(jsonPath("$.email").value("member@acme.com"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/organizations/{orgCode}/members/{userId}/details should return member details (legacy alias)")
    void getOrganizationMemberDetail_LegacyPath_ShouldReturnDetail() throws Exception {
        Jwt jwt = createJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
        when(organizationService.getOrganizationMemberDetail(eq(ORG_CODE), eq(TARGET_USER_ID), any(UserEntity.class)))
                .thenReturn(detailDto);

        mockMvc.perform(get("/api/v1/organizations/{orgCode}/members/{userId}/details", ORG_CODE, TARGET_USER_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(TARGET_USER_ID))
                .andExpect(jsonPath("$.email").value("member@acme.com"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/organizations/{orgCode}/members/{userId}/access-summary should return member access summary")
    void getOrganizationMemberAccessSummary_ShouldReturnSummary() throws Exception {
        Jwt jwt = createJwt();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        MemberAccessSummaryResponseDto summaryDto = MemberAccessSummaryResponseDto.builder()
                .userId(TARGET_USER_ID)
                .orgCode(ORG_CODE)
                .orgRole(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .teams(List.of())
                .workspaces(List.of())
                .directPermissions(List.of())
                .build();

        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
        when(organizationService.getOrganizationMemberAccessSummary(eq(ORG_CODE), eq(TARGET_USER_ID), any(UserEntity.class)))
                .thenReturn(summaryDto);

        mockMvc.perform(get("/api/v1/organizations/{orgCode}/members/{userId}/access-summary", ORG_CODE, TARGET_USER_ID)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(TARGET_USER_ID))
                .andExpect(jsonPath("$.orgCode").value(ORG_CODE))
                .andExpect(jsonPath("$.orgRole").value("ROLE_USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
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
}
