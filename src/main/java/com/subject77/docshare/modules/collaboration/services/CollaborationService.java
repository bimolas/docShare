package com.subject77.docshare.modules.collaboration.services;

import com.subject77.docshare.modules.collaboration.dto.CollaborationGroupParticipantResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CollaborationParticipantResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CollaborationResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CreateCollaborationRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.user.entities.UserEntity;

import java.util.List;

public interface CollaborationService {

    CollaborationResponseDto createCollaboration(CreateCollaborationRequestDto request, UserEntity currentUser);

    CollaborationResponseDto getCollaborationById(Long id, UserEntity currentUser);

    List<CollaborationResponseDto> getCollaborationsForOrganization(String orgCode, UserEntity currentUser);

    CollaborationResponseDto acceptCollaboration(Long id, UserEntity currentUser);

    CollaborationResponseDto declineCollaboration(Long id, UserEntity currentUser);

    CollaborationParticipantResponseDto addParticipant(Long collaborationId, String userId, UserEntity currentUser);

    void removeParticipant(Long collaborationId, String userId, UserEntity currentUser);

    List<CollaborationParticipantResponseDto> getParticipants(Long collaborationId, UserEntity currentUser);

    CollaborationGroupParticipantResponseDto addGroupParticipant(Long collaborationId, Long groupId, UserEntity currentUser);

    void removeGroupParticipant(Long collaborationId, Long groupId, UserEntity currentUser);

    List<CollaborationGroupParticipantResponseDto> getGroupParticipants(Long collaborationId, UserEntity currentUser);

    CollaborationParticipantResponseDto assignManager(Long collaborationId, String userId, UserEntity currentUser);

    CollaborationParticipantResponseDto removeManager(Long collaborationId, String userId, UserEntity currentUser);

    PermissionResponseDto grantCollaborationPermission(Long collaborationId, PermissionRequestDto request, UserEntity currentUser);

    PermissionResponseDto revokeCollaborationPermission(Long collaborationId, Long permissionId, UserEntity currentUser);

    List<PermissionResponseDto> getCollaborationPermissions(Long collaborationId, UserEntity currentUser);

    CollaborationResponseDto revokeCollaboration(Long collaborationId, UserEntity currentUser);
}

