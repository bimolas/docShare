package com.subject77.docshare.modules.folder.dto;

import com.subject77.docshare.modules.document.dto.DocumentResponse;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderContentResponseDto {
    private FolderResponseDto currentFolder;
    private List<FolderBreadcrumbDto> breadcrumbs;
    private List<FolderResponseDto> subFolders;
    private List<DocumentResponse> documents;
}
