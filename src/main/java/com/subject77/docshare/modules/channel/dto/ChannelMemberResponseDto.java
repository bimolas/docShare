package com.subject77.docshare.modules.channel.dto;

import com.subject77.docshare.modules.channel.entities.ChannelMemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMemberResponseDto {
    private Long id;
    private Long channelId;
    private String userId;
    private String userEmail;
    private String userName;
    private String userOrgCode;
    private ChannelMemberRole role;
    private Instant joinedAt;
}
