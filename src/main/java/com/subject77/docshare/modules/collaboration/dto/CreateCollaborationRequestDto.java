package com.subject77.docshare.modules.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCollaborationRequestDto {

    @NotBlank(message = "Collaboration name is required")
    private String name;

    @NotBlank(message = "Owner organization code is required")
    private String ownerOrgCode;

    @NotBlank(message = "Partner organization code is required")
    private String partnerOrgCode;

    @NotNull(message = "Root folder ID is required")
    private Long rootFolderId;

    @NotNull(message = "Start timestamp is required")
    private Instant startsAt;

    private Instant endsAt;
}
