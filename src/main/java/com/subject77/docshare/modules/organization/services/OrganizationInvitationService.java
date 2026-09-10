package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.modules.organization.dto.CreateOrganizationInvitationRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationInvitationResponseDto;
import com.subject77.docshare.modules.user.entities.UserEntity;

import java.util.List;

public interface OrganizationInvitationService {

    OrganizationInvitationResponseDto createInvitation(String orgCode, CreateOrganizationInvitationRequestDto request, UserEntity currentUser);

    OrganizationInvitationResponseDto acceptInvitation(String token, UserEntity currentUser);

    OrganizationInvitationResponseDto declineInvitation(String token, UserEntity currentUser);

    void revokeInvitation(String orgCode, Long invitationId, UserEntity currentUser);

    List<OrganizationInvitationResponseDto> getPendingInvitationsForOrganization(String orgCode, UserEntity currentUser);

    List<OrganizationInvitationResponseDto> getMyPendingInvitations(UserEntity currentUser);

    OrganizationInvitationResponseDto getInvitationByToken(String token);

    int processExpiredInvitations();
}
