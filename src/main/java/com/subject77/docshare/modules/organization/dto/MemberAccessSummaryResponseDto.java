package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.user.entities.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberAccessSummaryResponseDto {

    private String userId;
    private String email;
    private String orgCode;
    private Role orgRole;
    private MembershipStatus status;

    private List<TeamSummaryDto> teams;
    private List<WorkspaceSummaryDto> workspaces;
    private List<DirectPermissionSummaryDto> directPermissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamSummaryDto {
        private Long groupId;
        private String groupName;
        private GroupMemberRole role;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceSummaryDto {
        private String workspaceId;
        private String name;
        private String accessType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectPermissionSummaryDto {
        private Long permissionId;
        private String targetType;
        private Long targetId;
        private String targetName;
        private PermissionLevel permissionLevel;
    }
}
