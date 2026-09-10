package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.modules.organization.dto.*;
import com.subject77.docshare.modules.user.entities.UserEntity;
import java.util.List;

public interface TeamDelegationService {
    TeamAccessBoundaryResponseDto getTeamBoundary(String orgCode, Long teamId, UserEntity currentUser);
    List<TeamMemberAccessResponseDto> getMemberAccessList(String orgCode, Long teamId, UserEntity currentUser);
    TeamMemberAccessResponseDto grantMemberAccess(String orgCode, Long teamId, GrantMemberAccessRequestDto req, UserEntity currentUser);
    TeamMemberAccessResponseDto updateMemberAccess(String orgCode, Long teamId, Long permissionId, UpdateMemberAccessRequestDto req, UserEntity currentUser);
    void revokeMemberAccess(String orgCode, Long teamId, Long permissionId, UserEntity currentUser);
    ScopedResourceNodeDto getScopedResourceTree(String orgCode, Long teamId, UserEntity currentUser);
}
