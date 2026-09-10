package com.subject77.docshare.modules.workspace.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceManagerResponseDto {

    private Long id;
    private Long workspaceId;
    private String workspaceName;
    private String userId;
    private String userEmail;
    private Instant createdAt;
}
