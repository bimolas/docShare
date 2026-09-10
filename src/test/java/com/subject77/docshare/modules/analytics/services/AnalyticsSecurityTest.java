package com.subject77.docshare.modules.analytics.services;

import com.subject77.docshare.modules.analytics.dto.ActivityItemDto;
import com.subject77.docshare.modules.analytics.dto.DailyActivityDetailDto;
import com.subject77.docshare.modules.audit.entities.AccessLogEntity;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.repository.AccessLogRepository;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsSecurityTest {

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


    private UserEntity orgAdminUser;
    private UserEntity regularUser;
    private UserEntity crossOrgUser;
    private AccessLogEntity otherUserLog;
    private AccessLogEntity ownUserLog;
    private final String orgCode = "ACME";

    @BeforeEach
    void setUp() {
        orgAdminUser = UserEntity.builder()
                .id("admin-user-id")
                .email("admin@acme.com")
                .role(Role.ROLE_USER)
                .build();

        regularUser = UserEntity.builder()
                .id("regular-user-id")
                .email("member@acme.com")
                .role(Role.ROLE_USER)
                .build();

        crossOrgUser = UserEntity.builder()
                .id("other-user-id")
                .email("other@external.com")
                .role(Role.ROLE_USER)
                .build();

        otherUserLog = AccessLogEntity.builder()
                .id(101L)
                .accessorEmail("victim@acme.com")
                .action(Action.DOWNLOAD)
                .timestamp(Instant.now())
                .documentId(55L)
                .documentFileHash("sha256-hash-secret")
                .ipAddress("192.168.1.100")
                .details("Confidential document downloaded by victim")
                .orgCode(orgCode)
                .build();

        ownUserLog = AccessLogEntity.builder()
                .id(102L)
                .accessorEmail("member@acme.com")
                .action(Action.PREVIEW)
                .timestamp(Instant.now())
                .documentId(56L)
                .documentFileHash("sha256-hash-own")
                .ipAddress("10.0.0.1")
                .details("Own preview action")
                .orgCode(orgCode)
                .build();
    }

    @Test
    @DisplayName("A. ORG_ADMIN can retrieve organization-wide detailed activity with raw email, IP, and details")
    void getDailyActivityDetail_OrgAdmin_RetainsFullMetadata() {
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                "admin-user-id", orgCode, MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(
                "admin-user-id", orgCode))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .role(Role.ROLE_ORG_ADMIN)
                        .status(MembershipStatus.ACTIVE)
                        .build()));

        when(accessLogRepository.findByOrgCodeAndTimestampBetween(eq(orgCode), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(otherUserLog, ownUserLog));

        DailyActivityDetailDto result = analyticsService.getDailyActivityDetail(LocalDate.now().toString(), orgAdminUser, orgCode);

        assertThat(result.getTotalActions()).isEqualTo(2);
        assertThat(result.getActivities()).hasSize(2);

        ActivityItemDto item1 = result.getActivities().get(0);
        assertThat(item1.getAccessorEmail()).isEqualTo("victim@acme.com");
        assertThat(item1.getIpAddress()).isEqualTo("192.168.1.100");
        assertThat(item1.getDetails()).isEqualTo("Confidential document downloaded by victim");
    }

    @Test
    @DisplayName("B. Regular ROLE_USER cannot obtain another user's raw IP address (it is redacted to null)")
    void getDailyActivityDetail_RegularUser_CannotObtainOtherUserIpAddress() {
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                "regular-user-id", orgCode, MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(
                "regular-user-id", orgCode))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .role(Role.ROLE_USER)
                        .status(MembershipStatus.ACTIVE)
                        .build()));

        when(accessLogRepository.findByOrgCodeAndTimestampBetween(eq(orgCode), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(otherUserLog));

        DailyActivityDetailDto result = analyticsService.getDailyActivityDetail(LocalDate.now().toString(), regularUser, orgCode);

        assertThat(result.getActivities()).hasSize(1);
        ActivityItemDto otherUserActivity = result.getActivities().get(0);
        assertThat(otherUserActivity.getIpAddress())
                .as("Regular user must NOT see another user's IP address")
                .isNull();
    }

    @Test
    @DisplayName("C. Regular ROLE_USER cannot obtain another user's raw email or activity details")
    void getDailyActivityDetail_RegularUser_CannotObtainOtherUserEmailOrDetails() {
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                "regular-user-id", orgCode, MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(
                "regular-user-id", orgCode))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .role(Role.ROLE_USER)
                        .status(MembershipStatus.ACTIVE)
                        .build()));

        when(accessLogRepository.findByOrgCodeAndTimestampBetween(eq(orgCode), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(otherUserLog));

        DailyActivityDetailDto result = analyticsService.getDailyActivityDetail(LocalDate.now().toString(), regularUser, orgCode);

        assertThat(result.getActivities()).hasSize(1);
        ActivityItemDto otherUserActivity = result.getActivities().get(0);
        assertThat(otherUserActivity.getAccessorEmail())
                .as("Regular user must NOT see another user's email")
                .isNull();
        assertThat(otherUserActivity.getDetails())
                .as("Regular user must NOT see another user's activity details")
                .isNull();
    }

    @Test
    @DisplayName("D. Regular ROLE_USER can still view their own full activity details within org and personal scope")
    void getDailyActivityDetail_RegularUser_CanViewOwnActivityDetails() {
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                "regular-user-id", orgCode, MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(
                "regular-user-id", orgCode))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .role(Role.ROLE_USER)
                        .status(MembershipStatus.ACTIVE)
                        .build()));

        when(accessLogRepository.findByOrgCodeAndTimestampBetween(eq(orgCode), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(otherUserLog, ownUserLog));

        DailyActivityDetailDto result = analyticsService.getDailyActivityDetail(LocalDate.now().toString(), regularUser, orgCode);

        assertThat(result.getActivities()).hasSize(2);
        ActivityItemDto ownActivity = result.getActivities().get(1);
        assertThat(ownActivity.getAccessorEmail()).isEqualTo("member@acme.com");
        assertThat(ownActivity.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(ownActivity.getDetails()).isEqualTo("Own preview action");
    }

    @Test
    @DisplayName("E. Cross-organization analytics access remains blocked with AccessDeniedException")
    void getDailyActivityDetail_CrossOrg_Blocked() {
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                "other-user-id", orgCode, MembershipStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> analyticsService.getDailyActivityDetail(LocalDate.now().toString(), crossOrgUser, orgCode))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not an active member of organization: " + orgCode);
    }

    @Test
    @DisplayName("F. Cross-organization dashboard summary access is blocked with AccessDeniedException")
    void getOrganizationDashboardSummary_CrossOrg_Blocked() {
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                "other-user-id", orgCode, MembershipStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> analyticsService.getOrganizationDashboardSummary(crossOrgUser, orgCode))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not an active member of organization: " + orgCode);
    }

    @Test
    @DisplayName("G. Personal dashboard summary requires authenticated user")
    void getPersonalDashboardSummary_NullUser_ThrowsAccessDeniedException() {
        assertThatThrownBy(() -> analyticsService.getPersonalDashboardSummary(null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required to access personal dashboard");
    }

    @Test
    @DisplayName("H. Organization dashboard summary requires non-blank orgCode")
    void getOrganizationDashboardSummary_BlankOrgCode_ThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> analyticsService.getOrganizationDashboardSummary(regularUser, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Organization code is required");
    }
}

