package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.OrganizationInvitationStatus;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.user.entities.Role;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationInvitationResponseDto {

    private Long id;
    private String token;
    private String orgCode;
    private String organizationName;
    private String inviterUserId;
    private String inviterEmail;
    private String inviteeEmail;
    private String inviteeUserId;
    private Role role;
    private OrganizationInvitationStatus status;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant respondedAt;
    private String note;

    // Attached Onboarding Assignment Metadata
    private Long targetGroupId;
    private String targetGroupName;
    private GroupMemberRole targetGroupRole;
    private Long targetWorkspaceId;
    private String targetWorkspaceName;
    private Long targetFolderId;
    private String targetDocumentHash;
    private PermissionLevel onboardingPermissionLevel;
}
