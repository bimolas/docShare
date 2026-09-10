package com.subject77.docshare.modules.collaboration.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddGroupParticipantRequestDto {

    @NotNull(message = "Group ID is required")
    private Long groupId;
}
