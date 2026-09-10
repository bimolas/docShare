package com.subject77.docshare.modules.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferOrgAdminRequestDto {

    @NotBlank(message = "New administrator user ID is required")
    private String newAdminUserId;
}
