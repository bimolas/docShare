package com.subject77.docshare.modules.folder.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderPeekResponseDto {
    private Long folderId;
    private String folderName;
    private Long parentFolderId;
    private String ownerOrgCode;
    private String createdByUserId;
    private Instant createdAt;
    private int subfolderCount;
    private int fileCount;
    private long totalSizeBytes;
    private PermissionLevel currentUserPermission;
    private List<QuickItemPreviewDto> topItems;
}
