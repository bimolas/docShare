package com.subject77.docshare.modules.channel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMessageRequestDto {

    @NotBlank(message = "Updated message content cannot be blank")
    private String content;
}
