package com.subject77.docshare.modules.organization.dto;

import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationResponseDto {
    private String orgCode;
    private String name;
    private String createdByUserId;
    private Instant createdAt;
}
