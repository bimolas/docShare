package com.subject77.docshare.modules.dm.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessageReadReceiptDto {
    private String userId;
    private String userName;
    private String userEmail;
    private Instant readAt;
}
