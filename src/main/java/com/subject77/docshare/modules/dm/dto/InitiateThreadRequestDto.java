package com.subject77.docshare.modules.dm.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitiateThreadRequestDto {
    private String recipientUserId;
    private String recipientEmail;
    private String targetUserId;
    private String targetEmail;

    public String getTargetUserId() {
        return targetUserId != null ? targetUserId : recipientUserId;
    }

    public String getTargetEmail() {
        return targetEmail != null ? targetEmail : recipientEmail;
    }

    public String getRecipientUserId() {
        return recipientUserId != null ? recipientUserId : targetUserId;
    }

    public String getRecipientEmail() {
        return recipientEmail != null ? recipientEmail : targetEmail;
    }
}
