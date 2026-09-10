package com.subject77.docshare.modules.channel.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageAttachmentResponseDto {
    private Long attachmentId;
    private Long documentId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private boolean accessGranted;
    private boolean downloadable;
    private String downloadUrl;
    private String accessRestrictionReason;
}
