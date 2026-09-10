package com.subject77.docshare.modules.channel.dto;

import com.subject77.docshare.modules.channel.entities.ChannelMemberRole;
import com.subject77.docshare.modules.channel.entities.ChannelStatus;
import com.subject77.docshare.modules.channel.entities.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelResponseDto {
    private Long id;
    private String name;
    private String description;
    private ChannelType type;
    private ChannelStatus status;
    private String orgCode;
    private Long teamId;
    private Long projectWorkspaceId;
    private Long collaborationId;
    private String createdById;
    private String createdByEmail;
    private Instant createdAt;
    private Instant updatedAt;
    private long memberCount;
    private long unreadCount;

    private ChannelMemberRole currentUserRole;
    private boolean canManageChannel;
    private boolean canManageMembers;
    private boolean canModerateMessages;
    private boolean canArchive;
    private boolean canPost;
}
