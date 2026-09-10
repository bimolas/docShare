package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.permission.dto.AccessRequestEligibilityDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestActionDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestResponseDto;
import com.subject77.docshare.modules.permission.entities.AccessRequestStatus;
import com.subject77.docshare.modules.user.entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccessRequestService {

    AccessRequestEligibilityDto checkEligibility(Long documentId, String documentFileHash, Long folderId, UserEntity requester);

    AccessRequestResponseDto createAccessRequest(AccessRequestCreateDto dto, UserEntity requester);

    AccessRequestResponseDto approveAccessRequest(Long requestId, AccessRequestActionDto actionDto, UserEntity reviewer);

    AccessRequestResponseDto denyAccessRequest(Long requestId, AccessRequestActionDto actionDto, UserEntity reviewer);

    AccessRequestResponseDto cancelAccessRequest(Long requestId, UserEntity requester);

    Page<AccessRequestResponseDto> getMyAccessRequests(UserEntity currentUser, AccessRequestStatus status, Pageable pageable);

    Page<AccessRequestResponseDto> getMyAccessRequests(UserEntity currentUser, AccessRequestStatus status, String search, Pageable pageable);

    Page<AccessRequestResponseDto> getPendingReviews(UserEntity currentUser, Pageable pageable);

    Page<AccessRequestResponseDto> getReviewerAccessRequests(UserEntity currentUser, AccessRequestStatus status, String orgCode, Long workspaceId, String search, Pageable pageable);

    AccessRequestResponseDto getAccessRequestById(Long requestId, UserEntity currentUser);
}

