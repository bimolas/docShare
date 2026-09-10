package com.subject77.docshare.modules.audit.dto;

import com.subject77.docshare.modules.audit.entities.Action;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessLogRequestDto {

    @NotNull(message = "Action is required")
    private Action action;

    @NotBlank(message = "Document file hash is required")
    private String documentFileHash;

    private Long folderId;
    private String accessorEmail;

    @NotBlank(message = "IP address is required")
    private String ipAddress;

    @NotBlank(message = "User agent is required")
    private String userAgent;

    private String txHash;
}
