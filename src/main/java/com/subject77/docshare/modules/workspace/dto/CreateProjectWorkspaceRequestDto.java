package com.subject77.docshare.modules.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProjectWorkspaceRequestDto {

    @NotBlank(message = "Organization code is required")
    private String orgCode;

    @NotBlank(message = "Workspace name is required")
    private String name;

    private String description;

    private Long rootFolderId;
}
