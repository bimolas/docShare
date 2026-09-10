package com.subject77.docshare.modules.channel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddMessageReactionRequestDto {

    @NotBlank(message = "Reaction string cannot be blank")
    private String reaction;
}
