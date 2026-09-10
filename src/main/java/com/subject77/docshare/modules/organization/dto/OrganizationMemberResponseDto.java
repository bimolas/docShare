package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.user.entities.Role;
import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMemberResponseDto {
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private GroupMemberRole groupRole;
    private Instant joinedAt;
}
