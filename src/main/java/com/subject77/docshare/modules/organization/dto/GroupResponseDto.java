package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.organization.entities.GroupStatus;
import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponseDto {
    private Long id;
    private String name;
    private String description;
    private String orgCode;
    private GroupStatus status;
    private String createdByUserId;
    private Instant createdAt;
    private int memberCount;
}

