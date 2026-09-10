package com.subject77.docshare.modules.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateUploadRequest {

    @NotBlank(message = "File name must not be blank")
    private String fileName;

    @NotBlank(message = "Content type must not be blank")
    private String contentType;

    @NotNull(message = "File size is required")
    @Positive(message = "File size must be greater than zero")
    private Long fileSize;

    @NotBlank(message = "File hash is required")
    private String fileHash;

    private Long folderId; // Optional: null means root folder of the space

    private String orgCode; // Optional: null means Personal space
}
