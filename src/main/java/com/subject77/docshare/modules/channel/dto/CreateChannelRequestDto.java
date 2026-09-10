package com.subject77.docshare.modules.channel.dto;

import com.subject77.docshare.modules.channel.entities.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChannelRequestDto {
    private String name;
    private String description;
    private ChannelType type;
    private Boolean allowExternal;
    private String orgCode;
    private Long teamId;
    private Long projectWorkspaceId;
    private Long collaborationId;
}
