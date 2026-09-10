package com.subject77.docshare.modules.folder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderMoveRequestDto {
    private Long targetFolderId;
}
