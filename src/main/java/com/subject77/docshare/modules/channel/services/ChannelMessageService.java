package com.subject77.docshare.modules.channel.services;

import com.subject77.docshare.modules.channel.dto.*;
import com.subject77.docshare.modules.user.entities.UserEntity;
import org.springframework.data.domain.Pageable;

public interface ChannelMessageService {

    MessageResponseDto createMessage(Long channelId, CreateMessageRequestDto request, UserEntity currentUser);

    MessagePageResponseDto getMessages(Long channelId, Long parentMessageId, Pageable pageable, UserEntity currentUser);

    MessageResponseDto getMessage(Long channelId, Long messageId, UserEntity currentUser);

    MessageResponseDto updateMessage(Long channelId, Long messageId, UpdateMessageRequestDto request, UserEntity currentUser);

    MessageResponseDto deleteMessage(Long channelId, Long messageId, UserEntity currentUser);

    MessageReactionResponseDto addReaction(Long channelId, Long messageId, AddMessageReactionRequestDto request, UserEntity currentUser);

    void removeReaction(Long channelId, Long messageId, String reaction, UserEntity currentUser);

    MessageResponseDto addAttachmentToMessage(Long channelId, Long messageId, Long documentId, UserEntity currentUser);

    MessageResponseDto removeAttachmentFromMessage(Long channelId, Long messageId, Long attachmentId, UserEntity currentUser);

    /**
     * Records {@code currentUser} as having read all messages in the channel up to now.
     * Idempotent – calling it multiple times is safe.
     *
     * @param channelId   the target channel; user must be a member
     * @param currentUser the authenticated user
     */
    void markChannelAsRead(Long channelId, UserEntity currentUser);

    /**
     * Returns the number of unread messages in a channel for the given user.
     * "Unread" means: {@code createdAt > lastReadAt} and {@code author != currentUser}
     * and the message is not soft-deleted.
     *
     * @param channelId   the target channel; user must be a member
     * @param currentUser the authenticated user
     * @return unread message count (≥ 0)
     */
    long getChannelUnreadCount(Long channelId, UserEntity currentUser);
}

