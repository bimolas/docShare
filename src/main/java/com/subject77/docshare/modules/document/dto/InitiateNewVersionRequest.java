package com.subject77.docshare.modules.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateNewVersionRequest {
    @NotNull(message = "File size is required")
    private Long fileSize;

    @NotBlank(message = "Content type is required")
    private String contentType;

    @NotBlank(message = "Checksum (SHA-256) is required")
    private String checksumSha256;

    private String fileName;
}
