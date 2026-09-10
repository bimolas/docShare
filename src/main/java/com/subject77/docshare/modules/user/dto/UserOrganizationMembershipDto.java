package com.subject77.docshare.modules.user.dto;

import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.user.entities.Role;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOrganizationMembershipDto {
    private String orgCode;
    private String orgName;
    private Role role;
    private MembershipStatus status;
}
