package com.subject77.docshare.modules.user.dto;

import com.subject77.docshare.modules.user.entities.Role;
import lombok.*;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String orgCode;
    private Role role;
    private Instant createdAt;
    private List<UserOrganizationMembershipDto> activeOrganizations;
}
