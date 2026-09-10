package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupMemberRoleRequestDto {

    @NotNull(message = "Role is required")
    private GroupMemberRole role;
}
