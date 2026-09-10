package com.subject77.docshare.modules.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferManagerRequestDto {

    @NotBlank(message = "Target manager user ID is required")
    private String newManagerUserId;
}
