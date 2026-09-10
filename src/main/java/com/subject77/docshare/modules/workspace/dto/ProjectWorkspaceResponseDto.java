package com.subject77.docshare.modules.workspace.dto;

import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectWorkspaceResponseDto {

    private Long id;
    private String orgCode;
    private String name;
    private String description;
    private Long rootFolderId;
    private String rootFolderName;
    private ProjectWorkspaceStatus status;
    private String createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;
}
