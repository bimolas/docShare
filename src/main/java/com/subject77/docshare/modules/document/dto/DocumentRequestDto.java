package com.subject77.docshare.modules.document.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRequestDto {

    @NotBlank(message = "File name is required")
    private String fileName;

    private Long folderId;
}
