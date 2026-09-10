package com.subject77.docshare.modules.permission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLinkRevealResponseDto {

    private Long permissionId;

    private String shareLinkToken;

    private String shareUrl;

    private Instant expiresAt;
}
