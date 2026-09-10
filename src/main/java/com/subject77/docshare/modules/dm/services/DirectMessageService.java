package com.subject77.docshare.modules.dm.services;

import com.subject77.docshare.modules.dm.dto.*;
import com.subject77.docshare.modules.user.entities.UserEntity;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DirectMessageService {

    DirectMessageThreadResponseDto initiateOrGetThread(UserEntity currentUser, InitiateThreadRequestDto request);

    DirectMessageThreadPageResponseDto getUserThreads(UserEntity currentUser, Pageable pageable);

    DirectMessageThreadResponseDto getThread(UserEntity currentUser, Long threadId);

    DirectMessagePageResponseDto getThreadMessages(UserEntity currentUser, Long threadId, Pageable pageable);

    DirectMessageResponseDto sendMessage(UserEntity currentUser, Long threadId, CreateDirectMessageRequestDto request);

    DirectMessageResponseDto updateMessage(UserEntity currentUser, Long messageId, UpdateDirectMessageRequestDto request);

    void deleteMessage(UserEntity currentUser, Long messageId);

    void markThreadAsRead(UserEntity currentUser, Long threadId);

    UserMessagePrivacySettingsDto getPrivacySettings(UserEntity currentUser);

    UserMessagePrivacySettingsDto updatePrivacySettings(UserEntity currentUser, UserMessagePrivacySettingsDto dto);

    UserBlockResponseDto blockUser(UserEntity currentUser, String targetUserId);

    void unblockUser(UserEntity currentUser, String targetUserId);

    List<UserBlockResponseDto> getBlockedUsers(UserEntity currentUser);
}
