package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.user.entities.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMemberDetailResponseDto {

    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String orgCode;
    private Role role;
    private MembershipStatus status;
    private Instant joinedAt;
    private Instant statusUpdatedAt;
    private String updatedByUserId;
    private String statusReason;
}
