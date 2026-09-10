package com.subject77.docshare.modules.workspace.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProjectWorkspaceRequestDto {

    private String name;

    private String description;
}
