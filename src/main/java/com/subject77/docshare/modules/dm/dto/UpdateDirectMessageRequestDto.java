package com.subject77.docshare.modules.dm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDirectMessageRequestDto {

    @NotBlank(message = "Message content cannot be blank")
    private String content;
}
