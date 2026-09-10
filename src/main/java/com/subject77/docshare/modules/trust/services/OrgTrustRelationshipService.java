package com.subject77.docshare.modules.trust.services;

import com.subject77.docshare.modules.trust.dto.CreateTrustRequestDto;
import com.subject77.docshare.modules.trust.dto.TrustRelationshipResponseDto;
import com.subject77.docshare.modules.user.entities.UserEntity;

import java.util.List;

public interface OrgTrustRelationshipService {

    TrustRelationshipResponseDto createTrustRequest(CreateTrustRequestDto dto, UserEntity currentUser);

    TrustRelationshipResponseDto approveTrustRequest(Long trustId, UserEntity currentUser);

    TrustRelationshipResponseDto revokeTrustRelationship(Long trustId, UserEntity currentUser);

    List<TrustRelationshipResponseDto> getTrustRelationshipsForOrganization(String orgCode, UserEntity currentUser);

    TrustRelationshipResponseDto getTrustRelationship(Long trustId, UserEntity currentUser);

    boolean hasActiveTrust(String orgCode1, String orgCode2);
}
