package com.subject77.docshare.modules.channel.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMessageRequestDto {

    @NotBlank(message = "Message content cannot be blank")
    private String content;

    private Long parentMessageId;

    private List<Long> attachmentDocumentIds;
}

