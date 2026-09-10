package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.document.dto.DownloadUrlResponse;
import com.subject77.docshare.modules.permission.dto.ShareLinkRequestDto;
import com.subject77.docshare.modules.permission.dto.ShareLinkResponseDto;
import com.subject77.docshare.modules.permission.dto.ShareLinkRevealResponseDto;
import com.subject77.docshare.modules.user.entities.UserEntity;

public interface ShareService {

    ShareLinkResponseDto createShareLink(ShareLinkRequestDto request, UserEntity currentUser);

    ShareLinkRevealResponseDto revealShareLink(Long permissionId, UserEntity currentUser);

    ShareLinkResponseDto getShareLinkInfo(String shareLinkToken);

    Object accessSharedResource(String shareLinkToken, String password, Long folderId, UserEntity currentUser);

    default Object accessSharedResource(String shareLinkToken, String password, UserEntity currentUser) {
        return accessSharedResource(shareLinkToken, password, null, currentUser);
    }

    DownloadUrlResponse downloadSharedDocument(String shareLinkToken, String password, Long documentId, UserEntity currentUser);

    void revokeShareLink(String shareLinkToken, UserEntity currentUser);

    ShareLinkResponseDto excludeResourceFromShare(String shareLinkToken, Long documentId, Long folderId, UserEntity currentUser);

    default ShareLinkResponseDto excludeFileFromShare(String shareLinkToken, Long documentId, UserEntity currentUser) {
        return excludeResourceFromShare(shareLinkToken, documentId, null, currentUser);
    }
}
