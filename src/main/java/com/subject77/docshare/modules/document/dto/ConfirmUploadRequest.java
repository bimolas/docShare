package com.subject77.docshare.modules.document.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmUploadRequest {
    private Long documentId;
    private String clientHash;
    private String objectKey;
    private String fileName;
    private String contentType;
    private Long fileSize;
}
