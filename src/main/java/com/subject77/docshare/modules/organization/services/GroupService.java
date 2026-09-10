package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.modules.organization.dto.GroupRequestDto;
import com.subject77.docshare.modules.organization.dto.GroupResponseDto;
import com.subject77.docshare.modules.organization.dto.OrganizationMemberResponseDto;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.user.entities.UserEntity;

import java.util.List;

public interface GroupService {

    GroupResponseDto createGroup(String orgCode, GroupRequestDto request, UserEntity currentUser);

    GroupResponseDto updateGroup(String orgCode, Long groupId, GroupRequestDto request, UserEntity currentUser);

    GroupResponseDto archiveGroup(String orgCode, Long groupId, UserEntity currentUser);

    GroupResponseDto unarchiveGroup(String orgCode, Long groupId, UserEntity currentUser);

    List<GroupResponseDto> getOrganizationGroups(String orgCode, UserEntity currentUser);

    GroupResponseDto getGroupById(String orgCode, Long groupId, UserEntity currentUser);

    void addMemberToGroup(String orgCode, Long groupId, String targetUserId, UserEntity currentUser);

    void addMemberToGroup(String orgCode, Long groupId, String targetUserId, GroupMemberRole role, UserEntity currentUser);

    void updateMemberRole(String orgCode, Long groupId, String targetUserId, GroupMemberRole role, UserEntity currentUser);

    void removeMemberFromGroup(String orgCode, Long groupId, String targetUserId, UserEntity currentUser);

    List<OrganizationMemberResponseDto> getGroupMembers(String orgCode, Long groupId, UserEntity currentUser);

    void transferTeamManager(String orgCode, Long groupId, String newManagerUserId, UserEntity currentUser);

    void deleteGroup(String orgCode, Long groupId, UserEntity currentUser);
}

