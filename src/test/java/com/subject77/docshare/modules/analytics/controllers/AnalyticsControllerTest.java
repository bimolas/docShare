package com.subject77.docshare.modules.analytics.controllers;

import com.subject77.docshare.config.security.CurrentUserArgumentResolver;
import com.subject77.docshare.modules.analytics.dto.DailyActivitySummaryDto;
import com.subject77.docshare.modules.analytics.dto.OrganizationDashboardSummaryDto;
import com.subject77.docshare.modules.analytics.dto.PersonalDashboardSummaryDto;
import com.subject77.docshare.modules.analytics.services.AnalyticsService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
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
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsControllerTest {


    private MockMvc mockMvc;

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private UserService userService;

    @Mock
    private ObjectProvider<UserService> userServiceProvider;

    @InjectMocks
    private AnalyticsController analyticsController;

    private UserEntity mockUser;
    private static final String USER_ID = "usr-dashboard-1";

    @BeforeEach
    void setUp() {
        mockUser = UserEntity.builder()
                .id(USER_ID)
                .email("user@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .role(Role.ROLE_USER)
                .build();

        when(userServiceProvider.getIfAvailable()).thenReturn(userService);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(userServiceProvider);

        mockMvc = MockMvcBuilders.standaloneSetup(analyticsController)
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
                Map.of("sub", USER_ID, "email", "user@example.com")
        );
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(userService.getOrCreateCurrentUser(any(Jwt.class))).thenReturn(mockUser);
    }

    @Test
    @DisplayName("GET /api/v1/analytics/dashboard/personal - returns 200 with personal summary")
    void getPersonalDashboardSummary_Success() throws Exception {
        setupSecurityContext();

        PersonalDashboardSummaryDto summary = PersonalDashboardSummaryDto.builder()
                .usedStorageBytes(1024L * 1024L * 250L)
                .storageLimitBytes(10L * 1024 * 1024 * 1024)
                .storageUsagePercentage(2.44)
                .usedStorageFormatted("250.0 MB")
                .documentCount(12L)
                .folderCount(3L)
                .pendingAccessReviewsCount(2L)
                .myPendingAccessRequestsCount(1L)
                .activeDirectSharesCount(5L)
                .recentActivityTrend(List.of(
                        DailyActivitySummaryDto.builder().date("2026-09-09").count(4).intensity(2).build()
                ))
                .build();

        when(analyticsService.getPersonalDashboardSummary(any(UserEntity.class))).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/dashboard/personal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedStorageBytes").value(1024L * 1024L * 250L))
                .andExpect(jsonPath("$.storageLimitBytes").value(10L * 1024 * 1024 * 1024))
                .andExpect(jsonPath("$.storageUsagePercentage").value(2.44))
                .andExpect(jsonPath("$.usedStorageFormatted").value("250.0 MB"))
                .andExpect(jsonPath("$.documentCount").value(12))
                .andExpect(jsonPath("$.folderCount").value(3))
                .andExpect(jsonPath("$.pendingAccessReviewsCount").value(2))
                .andExpect(jsonPath("$.myPendingAccessRequestsCount").value(1))
                .andExpect(jsonPath("$.activeDirectSharesCount").value(5))
                .andExpect(jsonPath("$.recentActivityTrend[0].date").value("2026-09-09"))
                .andExpect(jsonPath("$.recentActivityTrend[0].count").value(4));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/dashboard/organization - returns 200 with organization summary")
    void getOrganizationDashboardSummary_Success() throws Exception {
        setupSecurityContext();

        OrganizationDashboardSummaryDto summary = OrganizationDashboardSummaryDto.builder()
                .orgCode("ORG-ACME")
                .orgName("Acme Global")
                .usedStorageBytes(10737418240L)
                .storageLimitBytes(50L * 1024 * 1024 * 1024)
                .storageUsagePercentage(20.0)
                .usedStorageFormatted("10.0 GB")
                .documentCount(250L)
                .folderCount(35L)
                .workspaceCount(6L)
                .teamCount(4L)
                .memberCount(30L)
                .pendingAccessRequestsCount(5L)
                .pendingInvitationsCount(3L)
                .recentActivityTrend(List.of(
                        DailyActivitySummaryDto.builder().date("2026-09-09").count(15).intensity(4).build()
                ))
                .build();

        when(analyticsService.getOrganizationDashboardSummary(any(UserEntity.class), eq("ORG-ACME")))
                .thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/dashboard/organization").param("orgCode", "ORG-ACME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgCode").value("ORG-ACME"))
                .andExpect(jsonPath("$.orgName").value("Acme Global"))
                .andExpect(jsonPath("$.usedStorageBytes").value(10737418240L))
                .andExpect(jsonPath("$.storageLimitBytes").value(50L * 1024 * 1024 * 1024))
                .andExpect(jsonPath("$.storageUsagePercentage").value(20.0))
                .andExpect(jsonPath("$.usedStorageFormatted").value("10.0 GB"))
                .andExpect(jsonPath("$.documentCount").value(250))
                .andExpect(jsonPath("$.folderCount").value(35))
                .andExpect(jsonPath("$.workspaceCount").value(6))
                .andExpect(jsonPath("$.teamCount").value(4))
                .andExpect(jsonPath("$.memberCount").value(30))
                .andExpect(jsonPath("$.pendingAccessRequestsCount").value(5))
                .andExpect(jsonPath("$.pendingInvitationsCount").value(3))
                .andExpect(jsonPath("$.recentActivityTrend[0].date").value("2026-09-09"))
                .andExpect(jsonPath("$.recentActivityTrend[0].count").value(15));
    }


    @Test
    @DisplayName("GET /api/v1/analytics/dashboard/organization - missing orgCode param returns 400")
    void getOrganizationDashboardSummary_MissingOrgCode_Returns400() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/dashboard/organization"))
                .andExpect(status().isBadRequest());
    }

}
