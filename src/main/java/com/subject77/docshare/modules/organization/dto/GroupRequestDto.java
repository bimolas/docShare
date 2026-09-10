package com.subject77.docshare.modules.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupRequestDto {

    @NotBlank(message = "Group name is required")
    private String name;

    private String description;

    @NotBlank(message = "Initial Team Manager user ID is required")
    private String initialManagerUserId;

    private List<String> initialMemberUserIds;
}
