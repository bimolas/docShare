package com.subject77.docshare.modules.permission.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLinkResponseDto {

    private Long permissionId;

    private String shareLinkToken;

    private Long documentId;

    private String documentName;

    private Long folderId;

    private String folderName;

    private PermissionType permissionType;

    private PermissionLevel permissionLevel;

    private boolean requiresPassword;

    private Instant expiresAt;

    private String targetUserEmail;

    private Instant createdAt;

    private String note;

    private boolean active;

    private String createdByUserId;

    private String createdByUserEmail;

    private Boolean canReveal;

    @JsonProperty("id")
    public Long getId() {
        return permissionId;
    }

    @JsonProperty("token")
    public String getToken() {
        return shareLinkToken;
    }

    @JsonProperty("passwordProtected")
    public boolean isPasswordProtected() {
        return requiresPassword;
    }
}

