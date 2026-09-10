package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.user.entities.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequestDto {

    @NotBlank(message = "User email is required")
    @Email(message = "Invalid email format")
    private String userEmail;

    @NotNull(message = "Role is required")
    private Role role;
}
