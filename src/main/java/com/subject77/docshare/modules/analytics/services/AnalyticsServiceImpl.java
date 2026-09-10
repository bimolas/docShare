package com.subject77.docshare.modules.analytics.services;

import com.subject77.docshare.modules.analytics.dto.ActivityItemDto;
import com.subject77.docshare.modules.analytics.dto.DailyActivityDetailDto;
import com.subject77.docshare.modules.analytics.dto.DailyActivitySummaryDto;
import com.subject77.docshare.modules.analytics.dto.OrganizationDashboardSummaryDto;
import com.subject77.docshare.modules.analytics.dto.PersonalDashboardSummaryDto;
import com.subject77.docshare.modules.analytics.dto.StorageUsageResponseDto;
import com.subject77.docshare.modules.audit.entities.AccessLogEntity;
import com.subject77.docshare.modules.audit.repository.AccessLogRepository;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationInvitationStatus;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationInvitationRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.entities.AccessRequestStatus;
import com.subject77.docshare.modules.permission.repository.AccessRequestRepository;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AccessLogRepository accessLogRepository;
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final OrganizationRepository organizationRepository;
    private final ProjectWorkspaceRepository projectWorkspaceRepository;
    private final GroupRepository groupRepository;
    private final OrganizationInvitationRepository organizationInvitationRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final PermissionRepository permissionRepository;

    private static final long DEFAULT_STORAGE_LIMIT = 10L * 1024 * 1024 * 1024; // 10 GB
    private static final long DEFAULT_PERSONAL_STORAGE_LIMIT = 10L * 1024 * 1024 * 1024; // 10 GB
    private static final long DEFAULT_ORGANIZATION_STORAGE_LIMIT = 50L * 1024 * 1024 * 1024; // 50 GB


    @Override
    public List<DailyActivitySummaryDto> getActivityHeatmap(int days, UserEntity currentUser, String orgCode) {
        if (days <= 0) {
            days = 365; // Default 1 year for GitHub-style heatmap
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1);

        Instant startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endInstant = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<AccessLogEntity> logs;
        if (orgCode != null && !orgCode.isBlank()) {
            validateUserInOrganization(currentUser, orgCode);
            logs = accessLogRepository.findByOrgCodeAndTimestampBetween(orgCode, startInstant, endInstant);
        } else {
            logs = accessLogRepository.findByAccessorEmailAndTimestampBetween(currentUser.getEmail(), startInstant, endInstant);
        }

        Map<String, Long> activityCountsByDate = logs.stream()
                .collect(Collectors.groupingBy(
                        log -> LocalDate.ofInstant(log.getTimestamp(), ZoneId.systemDefault()).toString(),
                        Collectors.counting()
                ));

        List<DailyActivitySummaryDto> heatmap = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            String dateStr = date.toString();
            long count = activityCountsByDate.getOrDefault(dateStr, 0L);
            int intensity = calculateIntensity(count);

            heatmap.add(DailyActivitySummaryDto.builder()
                    .date(dateStr)
                    .count(count)
                    .intensity(intensity)
                    .build());
        }

        return heatmap;
    }

    @Override
    public DailyActivityDetailDto getDailyActivityDetail(String dateStr, UserEntity currentUser, String orgCode) {
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            date = LocalDate.now();
        }

        Instant startInstant = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endInstant = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<AccessLogEntity> logs;
        boolean isOrgAdmin = false;
        if (orgCode != null && !orgCode.isBlank()) {
            validateUserInOrganization(currentUser, orgCode);
            isOrgAdmin = isUserAdminOfOrg(currentUser, orgCode);
            logs = accessLogRepository.findByOrgCodeAndTimestampBetween(orgCode, startInstant, endInstant);
        } else {
            logs = accessLogRepository.findByAccessorEmailAndTimestampBetween(currentUser.getEmail(), startInstant, endInstant);
        }

        final boolean orgAdminAccess = isOrgAdmin;
        final boolean isPersonalScope = (orgCode == null || orgCode.isBlank());

        List<ActivityItemDto> activities = logs.stream()
                .map(log -> {
                    boolean isOwn = currentUser != null && currentUser.getEmail() != null &&
                            currentUser.getEmail().equalsIgnoreCase(log.getAccessorEmail());
                    boolean allowFullMetadata = isPersonalScope || orgAdminAccess || isOwn;

                    return ActivityItemDto.builder()
                            .id(log.getId())
                            .action(log.getAction() != null ? log.getAction().name() : "UNKNOWN")
                            .timestamp(log.getTimestamp())
                            .documentId(log.getDocumentId())
                            .documentHash(log.getDocumentFileHash())
                            .accessorEmail(allowFullMetadata ? log.getAccessorEmail() : null)
                            .ipAddress(allowFullMetadata ? log.getIpAddress() : null)
                            .details(allowFullMetadata ? log.getDetails() : null)
                            .build();
                })
                .collect(Collectors.toList());

        return DailyActivityDetailDto.builder()
                .date(date.toString())
                .totalActions(activities.size())
                .activities(activities)
                .build();
    }

    @Override
    public StorageUsageResponseDto getStorageUsage(UserEntity currentUser, String orgCode) {
        long totalStorageBytes;
        long docCount;
        long folderCount;

        if (orgCode != null && !orgCode.isBlank()) {
            validateUserInOrganization(currentUser, orgCode);
            Long bytes = documentRepository.sumFileSizeByOrgCode(orgCode);
            totalStorageBytes = bytes != null ? bytes : 0L;
            docCount = documentRepository.countByOwnerOrg_OrgCode(orgCode);
            folderCount = folderRepository.countByOwnerOrg_OrgCode(orgCode);
        } else {
            Long bytes = documentRepository.sumFileSizeByUserId(currentUser.getId());
            totalStorageBytes = bytes != null ? bytes : 0L;
            docCount = documentRepository.countByUploadedBy_Id(currentUser.getId());
            folderCount = folderRepository.countByCreatedBy_Id(currentUser.getId());
        }

        return StorageUsageResponseDto.builder()
                .totalStorageBytes(totalStorageBytes)
                .documentCount(docCount)
                .folderCount(folderCount)
                .usedSpaceFormatted(formatBytes(totalStorageBytes))
                .storageLimitBytes(DEFAULT_STORAGE_LIMIT)
                .build();
    }

    @Override
    public PersonalDashboardSummaryDto getPersonalDashboardSummary(UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to access personal dashboard");
        }

        Long bytes = documentRepository.sumFileSizeByUserId(currentUser.getId());
        long usedBytes = bytes != null ? bytes : 0L;
        long docCount = documentRepository.countByUploadedBy_Id(currentUser.getId());
        long folderCount = folderRepository.countByCreatedBy_Id(currentUser.getId());

        double percentage = DEFAULT_PERSONAL_STORAGE_LIMIT > 0
                ? Math.round(((double) usedBytes / DEFAULT_PERSONAL_STORAGE_LIMIT) * 10000.0) / 100.0
                : 0.0;

        long pendingReviews = accessRequestRepository.countPendingByPersonalUserId(
                AccessRequestStatus.PENDING, currentUser.getId());
        long myPendingRequests = accessRequestRepository.countByRequester_IdAndStatus(
                currentUser.getId(), AccessRequestStatus.PENDING);
        long activeDirectShares = permissionRepository.countActiveDirectSharesForUser(
                currentUser.getId(), currentUser.getEmail(), Instant.now());

        List<DailyActivitySummaryDto> activityTrend = getActivityHeatmap(14, currentUser, null);

        return PersonalDashboardSummaryDto.builder()
                .usedStorageBytes(usedBytes)
                .storageLimitBytes(DEFAULT_PERSONAL_STORAGE_LIMIT)
                .storageUsagePercentage(percentage)
                .usedStorageFormatted(formatBytes(usedBytes))
                .documentCount(docCount)
                .folderCount(folderCount)
                .pendingAccessReviewsCount(pendingReviews)
                .myPendingAccessRequestsCount(myPendingRequests)
                .activeDirectSharesCount(activeDirectShares)
                .recentActivityTrend(activityTrend)
                .build();
    }

    @Override
    public OrganizationDashboardSummaryDto getOrganizationDashboardSummary(UserEntity currentUser, String orgCode) {
        if (orgCode == null || orgCode.isBlank()) {
            throw new IllegalArgumentException("Organization code is required");
        }

        validateUserInOrganization(currentUser, orgCode);

        String orgName = organizationRepository.findById(orgCode)
                .map(OrganizationEntity::getName)
                .orElse(orgCode);

        Long bytes = documentRepository.sumFileSizeByOrgCode(orgCode);
        long usedBytes = bytes != null ? bytes : 0L;
        long docCount = documentRepository.countByOwnerOrg_OrgCode(orgCode);
        long folderCount = folderRepository.countByOwnerOrg_OrgCode(orgCode);

        double percentage = DEFAULT_ORGANIZATION_STORAGE_LIMIT > 0
                ? Math.round(((double) usedBytes / DEFAULT_ORGANIZATION_STORAGE_LIMIT) * 10000.0) / 100.0
                : 0.0;

        long workspaceCount = projectWorkspaceRepository.countByOrganization_OrgCodeAndStatus(
                orgCode, ProjectWorkspaceStatus.ACTIVE);
        long teamCount = groupRepository.countByOrganization_OrgCodeAndStatus(
                orgCode, GroupStatus.ACTIVE);
        long memberCount = userOrganizationRepository.countByOrganization_OrgCodeAndStatus(
                orgCode, MembershipStatus.ACTIVE);

        long pendingAccessRequests = accessRequestRepository.countPendingByOrgCode(
                AccessRequestStatus.PENDING, orgCode);
        long pendingInvitations = organizationInvitationRepository.countByOrganization_OrgCodeAndStatus(
                orgCode, OrganizationInvitationStatus.PENDING);

        List<DailyActivitySummaryDto> activityTrend = getActivityHeatmap(14, currentUser, orgCode);

        return OrganizationDashboardSummaryDto.builder()
                .orgCode(orgCode)
                .orgName(orgName)
                .usedStorageBytes(usedBytes)
                .storageLimitBytes(DEFAULT_ORGANIZATION_STORAGE_LIMIT)
                .storageUsagePercentage(percentage)
                .usedStorageFormatted(formatBytes(usedBytes))
                .documentCount(docCount)
                .folderCount(folderCount)
                .workspaceCount(workspaceCount)
                .teamCount(teamCount)
                .memberCount(memberCount)
                .pendingAccessRequestsCount(pendingAccessRequests)
                .pendingInvitationsCount(pendingInvitations)
                .recentActivityTrend(activityTrend)
                .build();
    }


    private void validateUserInOrganization(UserEntity currentUser, String orgCode) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to access organization analytics");
        }
        if (currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }
        if (!userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                currentUser.getId(), orgCode, MembershipStatus.ACTIVE)) {
            throw new AccessDeniedException("User is not an active member of organization: " + orgCode);
        }
    }

    private boolean isUserAdminOfOrg(UserEntity currentUser, String orgCode) {
        if (currentUser == null || orgCode == null || orgCode.isBlank()) {
            return false;
        }
        if (currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return true;
        }
        return userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(
                currentUser.getId(), orgCode)
                .filter(uo -> uo.getStatus() == MembershipStatus.ACTIVE)
                .map(uo -> uo.getRole() == Role.ROLE_ORG_ADMIN)
                .orElse(false);
    }

    private int calculateIntensity(long count) {
        if (count <= 0) return 0;
        if (count <= 2) return 1;
        if (count <= 5) return 2;
        if (count <= 9) return 3;
        return 4;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }
}
