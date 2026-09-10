package com.subject77.docshare.modules.trust.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTrustRequestDto {

    @NotBlank(message = "Source organization code is required")
    private String sourceOrgCode;

    @NotBlank(message = "Target organization code is required")
    private String targetOrgCode;

    private Instant expiresAt;
}
