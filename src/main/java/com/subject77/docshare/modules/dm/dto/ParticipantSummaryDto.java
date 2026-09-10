package com.subject77.docshare.modules.dm.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipantSummaryDto {
    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String orgCode;
    private String orgName;
    private boolean external;
}
