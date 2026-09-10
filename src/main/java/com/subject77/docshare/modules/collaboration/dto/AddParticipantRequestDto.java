package com.subject77.docshare.modules.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddParticipantRequestDto {

    @NotBlank(message = "User ID is required")
    private String userId;
}
