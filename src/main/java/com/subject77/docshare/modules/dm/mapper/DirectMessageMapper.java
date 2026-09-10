package com.subject77.docshare.modules.dm.mapper;

import com.subject77.docshare.modules.dm.dto.*;
import com.subject77.docshare.modules.dm.entities.*;
import com.subject77.docshare.modules.user.entities.UserEntity;

import java.time.Instant;
import java.util.List;

public class DirectMessageMapper {

    public static ParticipantSummaryDto toParticipantSummaryDto(UserEntity user) {
        if (user == null) {
            return null;
        }
        return ParticipantSummaryDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .orgCode(user.getOrganization() != null ? user.getOrganization().getOrgCode() : null)
                .orgName(user.getOrganization() != null ? user.getOrganization().getName() : null)
                .build();
    }

    public static DirectMessageResponseDto toDirectMessageResponseDto(DirectMessageEntity entity) {
        return toDirectMessageResponseDto(entity, null, null, true);
    }

    public static DirectMessageResponseDto toDirectMessageResponseDto(
            DirectMessageEntity entity,
            List<DirectMessageReadReceiptEntity> receipts,
            String currentUserId,
            boolean readReceiptsAllowed) {
        if (entity == null) {
            return null;
        }
        boolean isDeleted = entity.getDeletedAt() != null;

        List<DirectMessageReadReceiptDto> receiptDtos = null;
        boolean readByRecipient = false;
        boolean readByCurrentUser = false;
        Instant readAt = null;

        if (readReceiptsAllowed && receipts != null) {
            receiptDtos = receipts.stream()
                    .map(r -> DirectMessageReadReceiptDto.builder()
                            .userId(r.getUser() != null ? r.getUser().getId() : null)
                            .userName(r.getUser() != null && r.getUser().getFirstName() != null
                                    ? r.getUser().getFirstName() + " " + r.getUser().getLastName()
                                    : (r.getUser() != null ? r.getUser().getEmail() : null))
                            .userEmail(r.getUser() != null ? r.getUser().getEmail() : null)
                            .readAt(r.getReadAt())
                            .build())
                    .toList();

            String senderId = entity.getSender() != null ? entity.getSender().getId() : null;
            for (DirectMessageReadReceiptEntity r : receipts) {
                if (r.getUser() != null) {
                    if (currentUserId != null && r.getUser().getId().equals(currentUserId)) {
                        readByCurrentUser = true;
                    }
                    if (senderId != null && !r.getUser().getId().equals(senderId)) {
                        readByRecipient = true;
                        readAt = r.getReadAt();
                    }
                }
            }
        }
        if (entity.getSender() != null && currentUserId != null && entity.getSender().getId().equals(currentUserId)) {
            readByCurrentUser = true;
        }

        return DirectMessageResponseDto.builder()
                .id(entity.getId())
                .threadId(entity.getThread() != null ? entity.getThread().getId() : null)
                .sender(toParticipantSummaryDto(entity.getSender()))
                .content(isDeleted ? "This message was deleted." : entity.getContent())
                .edited(entity.isEdited())
                .deleted(isDeleted)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .readByRecipient(readByRecipient)
                .readByCurrentUser(readByCurrentUser)
                .readAt(readAt)
                .readReceipts(receiptDtos)
                .build();
    }

    public static DirectMessageThreadResponseDto toDirectMessageThreadResponseDto(
            DirectMessageThreadEntity entity,
            UserEntity otherParticipant,
            DirectMessageResponseDto lastMessage,
            long unreadCount) {
        if (entity == null) {
            return null;
        }
        return DirectMessageThreadResponseDto.builder()
                .id(entity.getId())
                .participant(toParticipantSummaryDto(otherParticipant))
                .lastMessage(lastMessage)
                .unreadCount(unreadCount)
                .lastMessageAt(entity.getLastMessageAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public static UserMessagePrivacySettingsDto toUserMessagePrivacySettingsDto(UserMessagePrivacySettingsEntity entity) {
        if (entity == null) {
            return UserMessagePrivacySettingsDto.builder()
                    .allowInternalDms(true)
                    .allowExternalDms(true)
                    .readReceiptsEnabled(true)
                    .build();
        }
        return UserMessagePrivacySettingsDto.builder()
                .allowInternalDms(entity.isAllowInternalDms())
                .allowExternalDms(entity.isAllowExternalDms())
                .readReceiptsEnabled(entity.isReadReceiptsEnabled())
                .build();
    }

    public static UserBlockResponseDto toUserBlockResponseDto(UserBlockEntity entity) {
        if (entity == null) {
            return null;
        }
        return UserBlockResponseDto.builder()
                .id(entity.getId())
                .blockedUser(toParticipantSummaryDto(entity.getBlockedUser()))
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
