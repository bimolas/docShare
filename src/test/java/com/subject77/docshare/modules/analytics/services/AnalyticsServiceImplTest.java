package com.subject77.docshare.modules.analytics.services;

import com.subject77.docshare.modules.analytics.dto.DailyActivityDetailDto;
import com.subject77.docshare.modules.analytics.dto.DailyActivitySummaryDto;
import com.subject77.docshare.modules.analytics.dto.StorageUsageResponseDto;
import com.subject77.docshare.modules.audit.entities.AccessLogEntity;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.repository.AccessLogRepository;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock
    private AccessLogRepository accessLogRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private com.subject77.docshare.modules.organization.repository.OrganizationRepository organizationRepository;

    @Mock
    private com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository projectWorkspaceRepository;

    @Mock
    private com.subject77.docshare.modules.organization.repository.GroupRepository groupRepository;

    @Mock
    private com.subject77.docshare.modules.organization.repository.OrganizationInvitationRepository organizationInvitationRepository;

    @Mock
    private com.subject77.docshare.modules.permission.repository.AccessRequestRepository accessRequestRepository;

    @Mock
    private com.subject77.docshare.modules.permission.repository.PermissionRepository permissionRepository;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;


    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = UserEntity.builder()
                .id("user-123")
                .email("testuser@docshare.com")
                .build();
    }

    @Test
    @DisplayName("Should generate activity heatmap for specified days")
    void getActivityHeatmap_Success() {
        AccessLogEntity log1 = AccessLogEntity.builder()
                .id(1L)
                .accessorEmail("testuser@docshare.com")
                .action(Action.PREVIEW)
                .timestamp(Instant.now())
                .build();

        when(accessLogRepository.findByAccessorEmailAndTimestampBetween(eq("testuser@docshare.com"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(log1));

        List<DailyActivitySummaryDto> heatmap = analyticsService.getActivityHeatmap(7, user, null);

        assertThat(heatmap).hasSize(7);
        DailyActivitySummaryDto todaySummary = heatmap.get(heatmap.size() - 1);
        assertThat(todaySummary.getDate()).isEqualTo(LocalDate.now().toString());
        assertThat(todaySummary.getCount()).isEqualTo(1L);
        assertThat(todaySummary.getIntensity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should fetch daily activity details for specific date")
    void getDailyActivityDetail_Success() {
        String todayStr = LocalDate.now().toString();
        AccessLogEntity log1 = AccessLogEntity.builder()
                .id(10L)
                .accessorEmail("testuser@docshare.com")
                .action(Action.DOWNLOAD)
                .timestamp(Instant.now())
                .documentFileHash("hash123")
                .ipAddress("127.0.0.1")
                .details("Downloaded document")
                .build();

        when(accessLogRepository.findByAccessorEmailAndTimestampBetween(eq("testuser@docshare.com"), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(log1));

        DailyActivityDetailDto detail = analyticsService.getDailyActivityDetail(todayStr, user, null);

        assertThat(detail.getDate()).isEqualTo(todayStr);
        assertThat(detail.getTotalActions()).isEqualTo(1);
        assertThat(detail.getActivities()).hasSize(1);
        assertThat(detail.getActivities().get(0).getAction()).isEqualTo("DOWNLOAD");
    }

    @Test
    @DisplayName("Should return storage usage metrics for personal space")
    void getStorageUsage_Personal_Success() {
        when(documentRepository.sumFileSizeByUserId("user-123")).thenReturn(2048L * 1024L);
        when(documentRepository.countByUploadedBy_Id("user-123")).thenReturn(5L);
        when(folderRepository.countByCreatedBy_Id("user-123")).thenReturn(2L);

        StorageUsageResponseDto storageUsage = analyticsService.getStorageUsage(user, null);

        assertThat(storageUsage.getTotalStorageBytes()).isEqualTo(2048L * 1024L);
        assertThat(storageUsage.getDocumentCount()).isEqualTo(5L);
        assertThat(storageUsage.getFolderCount()).isEqualTo(2L);
        assertThat(storageUsage.getUsedSpaceFormatted()).isEqualTo("2.0 MB");
    }

    @Test
    @DisplayName("Should return storage usage metrics for organization space when user is member")
    void getStorageUsage_Org_Success() {
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-123", "ORG1", MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(documentRepository.sumFileSizeByOrgCode("ORG1")).thenReturn(1073741824L); // 1 GB
        when(documentRepository.countByOwnerOrg_OrgCode("ORG1")).thenReturn(50L);
        when(folderRepository.countByOwnerOrg_OrgCode("ORG1")).thenReturn(10L);

        StorageUsageResponseDto storageUsage = analyticsService.getStorageUsage(user, "ORG1");

        assertThat(storageUsage.getTotalStorageBytes()).isEqualTo(1073741824L);
        assertThat(storageUsage.getDocumentCount()).isEqualTo(50L);
        assertThat(storageUsage.getFolderCount()).isEqualTo(10L);
        assertThat(storageUsage.getUsedSpaceFormatted()).isEqualTo("1.0 GB");
    }

    @Test
    @DisplayName("Should deny organization storage metrics when user is not a member")
    void getStorageUsage_Org_UnrelatedUser_ThrowsAccessDeniedException() {
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-123", "ORG1", MembershipStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> analyticsService.getStorageUsage(user, "ORG1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not an active member of organization: ORG1");
    }

    @Test
    @DisplayName("Should deny organization heatmap when user is not a member")
    void getActivityHeatmap_Org_UnrelatedUser_ThrowsAccessDeniedException() {
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-123", "ORG1", MembershipStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> analyticsService.getActivityHeatmap(7, user, "ORG1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not an active member of organization: ORG1");
    }

    @Test
    @DisplayName("Should deny organization daily details when user is not a member")
    void getDailyActivityDetail_Org_UnrelatedUser_ThrowsAccessDeniedException() {
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-123", "ORG1", MembershipStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> analyticsService.getDailyActivityDetail("2026-09-06", user, "ORG1"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not an active member of organization: ORG1");
    }

    @Test
    @DisplayName("Should permit super admin to access organization storage metrics without membership")
    void getStorageUsage_Org_SuperAdmin_Success() {
        UserEntity superAdmin = UserEntity.builder()
                .id("admin-999")
                .email("super@docshare.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(documentRepository.sumFileSizeByOrgCode("ORG1")).thenReturn(1024L);
        when(documentRepository.countByOwnerOrg_OrgCode("ORG1")).thenReturn(1L);
        when(folderRepository.countByOwnerOrg_OrgCode("ORG1")).thenReturn(1L);

        StorageUsageResponseDto storageUsage = analyticsService.getStorageUsage(superAdmin, "ORG1");

        assertThat(storageUsage.getTotalStorageBytes()).isEqualTo(1024L);
    }

    @Test
    @DisplayName("Should return personal dashboard summary with all metrics and 14-day trend")
    void getPersonalDashboardSummary_Success() {
        when(documentRepository.sumFileSizeByUserId("user-123")).thenReturn(1024L * 1024L * 500L); // 500 MB
        when(documentRepository.countByUploadedBy_Id("user-123")).thenReturn(24L);
        when(folderRepository.countByCreatedBy_Id("user-123")).thenReturn(6L);
        when(accessRequestRepository.countPendingByPersonalUserId(
                com.subject77.docshare.modules.permission.entities.AccessRequestStatus.PENDING, "user-123"))
                .thenReturn(3L);
        when(accessRequestRepository.countByRequester_IdAndStatus(
                "user-123", com.subject77.docshare.modules.permission.entities.AccessRequestStatus.PENDING))
                .thenReturn(1L);
        when(permissionRepository.countActiveDirectSharesForUser(eq("user-123"), eq("testuser@docshare.com"), any(Instant.class)))
                .thenReturn(8L);
        when(accessLogRepository.findByAccessorEmailAndTimestampBetween(eq("testuser@docshare.com"), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        com.subject77.docshare.modules.analytics.dto.PersonalDashboardSummaryDto summary =
                analyticsService.getPersonalDashboardSummary(user);

        assertThat(summary).isNotNull();
        assertThat(summary.getUsedStorageBytes()).isEqualTo(1024L * 1024L * 500L);
        assertThat(summary.getStorageLimitBytes()).isEqualTo(10L * 1024 * 1024 * 1024);
        assertThat(summary.getStorageUsagePercentage()).isEqualTo(4.88);
        assertThat(summary.getUsedStorageFormatted()).isEqualTo("500.0 MB");
        assertThat(summary.getDocumentCount()).isEqualTo(24L);
        assertThat(summary.getFolderCount()).isEqualTo(6L);
        assertThat(summary.getPendingAccessReviewsCount()).isEqualTo(3L);
        assertThat(summary.getMyPendingAccessRequestsCount()).isEqualTo(1L);
        assertThat(summary.getActiveDirectSharesCount()).isEqualTo(8L);
        assertThat(summary.getRecentActivityTrend()).hasSize(14);
    }

    @Test
    @DisplayName("Should return organization dashboard summary with all metrics, workspaces, teams and 14-day trend")
    void getOrganizationDashboardSummary_Success() {
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-123", "ORG1", MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(organizationRepository.findById("ORG1"))
                .thenReturn(Optional.of(com.subject77.docshare.modules.organization.entities.OrganizationEntity.builder()
                        .orgCode("ORG1")
                        .name("Acme Corp")
                        .build()));
        when(documentRepository.sumFileSizeByOrgCode("ORG1")).thenReturn(10737418240L); // 10 GB
        when(documentRepository.countByOwnerOrg_OrgCode("ORG1")).thenReturn(350L);
        when(folderRepository.countByOwnerOrg_OrgCode("ORG1")).thenReturn(45L);
        when(projectWorkspaceRepository.countByOrganization_OrgCodeAndStatus(
                "ORG1", com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus.ACTIVE))
                .thenReturn(8L);
        when(groupRepository.countByOrganization_OrgCodeAndStatus(
                "ORG1", com.subject77.docshare.modules.organization.entities.GroupStatus.ACTIVE))
                .thenReturn(5L);
        when(userOrganizationRepository.countByOrganization_OrgCodeAndStatus(
                "ORG1", MembershipStatus.ACTIVE))
                .thenReturn(42L);
        when(accessRequestRepository.countPendingByOrgCode(
                com.subject77.docshare.modules.permission.entities.AccessRequestStatus.PENDING, "ORG1"))
                .thenReturn(7L);
        when(organizationInvitationRepository.countByOrganization_OrgCodeAndStatus(
                "ORG1", com.subject77.docshare.modules.organization.entities.OrganizationInvitationStatus.PENDING))
                .thenReturn(4L);
        when(accessLogRepository.findByOrgCodeAndTimestampBetween(eq("ORG1"), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        com.subject77.docshare.modules.analytics.dto.OrganizationDashboardSummaryDto summary =
                analyticsService.getOrganizationDashboardSummary(user, "ORG1");

        assertThat(summary).isNotNull();
        assertThat(summary.getOrgCode()).isEqualTo("ORG1");
        assertThat(summary.getOrgName()).isEqualTo("Acme Corp");
        assertThat(summary.getUsedStorageBytes()).isEqualTo(10737418240L);
        assertThat(summary.getStorageLimitBytes()).isEqualTo(50L * 1024 * 1024 * 1024);
        assertThat(summary.getStorageUsagePercentage()).isEqualTo(20.0);
        assertThat(summary.getUsedStorageFormatted()).isEqualTo("10.0 GB");
        assertThat(summary.getDocumentCount()).isEqualTo(350L);
        assertThat(summary.getFolderCount()).isEqualTo(45L);
        assertThat(summary.getWorkspaceCount()).isEqualTo(8L);
        assertThat(summary.getTeamCount()).isEqualTo(5L);
        assertThat(summary.getMemberCount()).isEqualTo(42L);
        assertThat(summary.getPendingAccessRequestsCount()).isEqualTo(7L);
        assertThat(summary.getPendingInvitationsCount()).isEqualTo(4L);
        assertThat(summary.getRecentActivityTrend()).hasSize(14);
    }

    @Test
    @DisplayName("Should permit super admin to access organization dashboard summary without membership")
    void getOrganizationDashboardSummary_SuperAdmin_Success() {
        UserEntity superAdmin = UserEntity.builder()
                .id("admin-999")
                .email("super@docshare.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(organizationRepository.findById("ORG1"))
                .thenReturn(Optional.of(com.subject77.docshare.modules.organization.entities.OrganizationEntity.builder()
                        .orgCode("ORG1")
                        .name("Acme Corp")
                        .build()));
        when(documentRepository.sumFileSizeByOrgCode("ORG1")).thenReturn(0L);
        when(documentRepository.countByOwnerOrg_OrgCode("ORG1")).thenReturn(0L);
        when(folderRepository.countByOwnerOrg_OrgCode("ORG1")).thenReturn(0L);
        when(projectWorkspaceRepository.countByOrganization_OrgCodeAndStatus(
                "ORG1", com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus.ACTIVE))
                .thenReturn(0L);
        when(groupRepository.countByOrganization_OrgCodeAndStatus(
                "ORG1", com.subject77.docshare.modules.organization.entities.GroupStatus.ACTIVE))
                .thenReturn(0L);
        when(userOrganizationRepository.countByOrganization_OrgCodeAndStatus(
                "ORG1", MembershipStatus.ACTIVE))
                .thenReturn(1L);
        when(accessRequestRepository.countPendingByOrgCode(
                com.subject77.docshare.modules.permission.entities.AccessRequestStatus.PENDING, "ORG1"))
                .thenReturn(0L);
        when(organizationInvitationRepository.countByOrganization_OrgCodeAndStatus(
                "ORG1", com.subject77.docshare.modules.organization.entities.OrganizationInvitationStatus.PENDING))
                .thenReturn(0L);
        when(accessLogRepository.findByOrgCodeAndTimestampBetween(eq("ORG1"), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        com.subject77.docshare.modules.analytics.dto.OrganizationDashboardSummaryDto summary =
                analyticsService.getOrganizationDashboardSummary(superAdmin, "ORG1");

        assertThat(summary).isNotNull();
        assertThat(summary.getOrgCode()).isEqualTo("ORG1");
        assertThat(summary.getMemberCount()).isEqualTo(1L);
    }
}

