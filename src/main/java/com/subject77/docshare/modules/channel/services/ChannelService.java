package com.subject77.docshare.modules.channel.services;

import com.subject77.docshare.modules.channel.dto.*;
import com.subject77.docshare.modules.channel.entities.ChannelEntity;
import com.subject77.docshare.modules.user.entities.UserEntity;

import java.util.List;

public interface ChannelService {

    ChannelResponseDto createChannel(CreateChannelRequestDto request, UserEntity currentUser);

    ChannelResponseDto getChannel(Long channelId, UserEntity currentUser);

    List<ChannelResponseDto> listChannels(String orgCode, Long teamId, Long projectWorkspaceId, Long collaborationId, UserEntity currentUser);

    ChannelResponseDto updateChannel(Long channelId, UpdateChannelRequestDto request, UserEntity currentUser);

    ChannelResponseDto archiveChannel(Long channelId, UserEntity currentUser);

    ChannelMemberResponseDto addMember(Long channelId, AddChannelMemberRequestDto request, UserEntity currentUser);

    void removeMember(Long channelId, String userId, UserEntity currentUser);

    List<ChannelMemberResponseDto> listMembers(Long channelId, UserEntity currentUser);

    ChannelMemberResponseDto updateMemberRole(Long channelId, String userId, UpdateChannelMemberRoleRequestDto request, UserEntity currentUser);

    void verifyChannelModeratorOrAdmin(ChannelEntity channel, UserEntity user);
}
