package com.subject77.docshare.modules.folder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickItemPreviewDto {
    private Long id;
    private String name;
    private String itemType; // FOLDER or DOCUMENT
    private Long sizeInBytes; // null for folders, file size for documents
    private Instant modifiedAt;
}
