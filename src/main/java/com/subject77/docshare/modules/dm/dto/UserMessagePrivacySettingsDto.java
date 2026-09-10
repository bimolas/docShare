package com.subject77.docshare.modules.dm.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMessagePrivacySettingsDto {
    private boolean allowInternalDms;
    private boolean allowExternalDms;
    private boolean readReceiptsEnabled;
}
