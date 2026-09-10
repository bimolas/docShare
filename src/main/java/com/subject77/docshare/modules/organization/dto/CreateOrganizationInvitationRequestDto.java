package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.user.entities.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrganizationInvitationRequestDto {

    @NotBlank(message = "Invitee email is required")
    @Email(message = "Invalid email format")
    private String inviteeEmail;

    private Role role;

    private Integer expirationDays;

    private String note;

    // Optional Onboarding Assignments
    private Long targetGroupId;

    private GroupMemberRole targetGroupRole;

    private Long targetWorkspaceId;

    private Long targetFolderId;

    private String targetDocumentHash;

    private PermissionLevel onboardingPermissionLevel;
}
