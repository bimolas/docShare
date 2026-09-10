package com.subject77.docshare.modules.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationRequestDto {

    @NotBlank(message = "Organization code is required")
    private String orgCode;

    @NotBlank(message = "Organization name is required")
    private String name;
}
