package com.subject77.docshare.modules.folder.dto;

import com.subject77.docshare.modules.folder.entities.SubspaceType;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderRequestDto {

    @NotBlank(message = "Folder name is required")
    private String name;

    private Long parentFolderId;

    private SubspaceType subspaceType;
}

