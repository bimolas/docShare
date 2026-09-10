package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.user.entities.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberRoleRequestDto {

    @NotNull(message = "Role is required")
    private Role role;
}
