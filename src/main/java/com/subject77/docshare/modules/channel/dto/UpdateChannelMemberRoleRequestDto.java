package com.subject77.docshare.modules.channel.dto;

import com.subject77.docshare.modules.channel.entities.ChannelMemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateChannelMemberRoleRequestDto {
    private ChannelMemberRole role;
}
