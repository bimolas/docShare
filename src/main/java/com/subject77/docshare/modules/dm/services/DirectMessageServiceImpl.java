package com.subject77.docshare.modules.dm.services;

import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.dm.dto.*;
import com.subject77.docshare.modules.dm.entities.*;
import com.subject77.docshare.modules.dm.mapper.DirectMessageMapper;
import com.subject77.docshare.modules.dm.repository.*;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.subject77.docshare.modules.communication.events.DirectMessageReadReceiptEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectMessageServiceImpl implements DirectMessageService {

    private final DirectMessageThreadRepository directMessageThreadRepository;
    private final DirectMessageRepository directMessageRepository;
    private final UserMessagePrivacySettingsRepository userMessagePrivacySettingsRepository;
    private final UserBlockRepository userBlockRepository;
    private final DirectMessageReadStateRepository directMessageReadStateRepository;
    private final DirectMessageReadReceiptRepository directMessageReadReceiptRepository;
    private final UserRepository userRepository;
    private final OrgTrustRelationshipService orgTrustRelationshipService;
    private final CollaborationRepository collaborationRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public DirectMessageThreadResponseDto initiateOrGetThread(UserEntity currentUser, InitiateThreadRequestDto request) {
        UserEntity targetUser = resolveTargetUser(request);
        if (currentUser.getId().equalsIgnoreCase(targetUser.getId())) {
            throw new IllegalArgumentException("Cannot send direct messages to yourself");
        }

        validateCommunicationEligibility(currentUser, targetUser);

        UserEntity user1 = currentUser.getId().compareTo(targetUser.getId()) < 0 ? currentUser : targetUser;
        UserEntity user2 = currentUser.getId().compareTo(targetUser.getId()) < 0 ? targetUser : currentUser;

        DirectMessageThreadEntity thread = directMessageThreadRepository
                .findThreadBetweenUsers(user1.getId(), user2.getId())
                .orElseGet(() -> directMessageThreadRepository.save(
                        DirectMessageThreadEntity.builder()
                                .user1(user1)
                                .user2(user2)
                                .lastMessageAt(Instant.now())
                                .build()
                ));

        return buildThreadResponseDto(thread, currentUser);
    }

    @Override
    public DirectMessageThreadPageResponseDto getUserThreads(UserEntity currentUser, Pageable pageable) {
        Page<DirectMessageThreadEntity> threadPage = directMessageThreadRepository.findThreadsByUserId(currentUser.getId(), pageable);
        List<DirectMessageThreadResponseDto> threads = threadPage.getContent().stream()
                .map(thread -> buildThreadResponseDto(thread, currentUser))
                .toList();

        return DirectMessageThreadPageResponseDto.builder()
                .threads(threads)
                .pageNumber(threadPage.getNumber())
                .pageSize(threadPage.getSize())
                .totalElements(threadPage.getTotalElements())
                .totalPages(threadPage.getTotalPages())
                .hasNext(threadPage.hasNext())
                .hasPrevious(threadPage.hasPrevious())
                .build();
    }

    @Override
    public DirectMessageThreadResponseDto getThread(UserEntity currentUser, Long threadId) {
        DirectMessageThreadEntity thread = getThreadAndVerifyParticipant(threadId, currentUser);
        return buildThreadResponseDto(thread, currentUser);
    }

    @Override
    @Transactional
    public DirectMessagePageResponseDto getThreadMessages(UserEntity currentUser, Long threadId, Pageable pageable) {
        DirectMessageThreadEntity thread = getThreadAndVerifyParticipant(threadId, currentUser);
        markThreadAsRead(currentUser, thread.getId());

        Page<DirectMessageEntity> messagesPage = directMessageRepository.findByThread_IdOrderByCreatedAtDesc(threadId, pageable);
        List<DirectMessageResponseDto> messages = messagesPage.getContent().stream()
                .map(m -> mapToDirectMessageResponseDto(m, currentUser))
                .toList();

        return DirectMessagePageResponseDto.builder()
                .messages(messages)
                .pageNumber(messagesPage.getNumber())
                .pageSize(messagesPage.getSize())
                .totalElements(messagesPage.getTotalElements())
                .totalPages(messagesPage.getTotalPages())
                .hasNext(messagesPage.hasNext())
                .hasPrevious(messagesPage.hasPrevious())
                .build();
    }

    @Override
    @Transactional
    public DirectMessageResponseDto sendMessage(UserEntity currentUser, Long threadId, CreateDirectMessageRequestDto request) {
        DirectMessageThreadEntity thread = getThreadAndVerifyParticipant(threadId, currentUser);
        UserEntity recipient = thread.getUser1().getId().equals(currentUser.getId()) ? thread.getUser2() : thread.getUser1();

        validateCommunicationEligibility(currentUser, recipient);

        DirectMessageEntity message = DirectMessageEntity.builder()
                .thread(thread)
                .sender(currentUser)
                .content(request.getContent())
                .edited(false)
                .build();

        DirectMessageEntity savedMessage = directMessageRepository.save(message);

        thread.setLastMessageAt(Instant.now());
        directMessageThreadRepository.save(thread);

        markThreadAsRead(currentUser, threadId);

        if (notificationService != null) {
            try {
                String senderName = (currentUser.getFirstName() != null && !currentUser.getFirstName().isBlank())
                        ? currentUser.getFirstName() + (currentUser.getLastName() != null ? " " + currentUser.getLastName() : "")
                        : currentUser.getEmail();
                String dmIdempotencyKey = "DM_RECEIVED:" + thread.getId() + ":" + savedMessage.getId() + ":" + recipient.getId();
                notificationService.createNotification(
                        recipient,
                        currentUser,
                        NotificationType.DM_RECEIVED,
                        "New Direct Message",
                        senderName + " sent you a direct message",
                        NotificationSourceType.DM,
                        thread.getId().toString(),
                        null,
                        null,
                        dmIdempotencyKey
                );
            } catch (Exception e) {
                log.error("Failed to send DM_RECEIVED notification to user {}: {}", recipient.getId(), e.getMessage());
            }
        }

        return mapToDirectMessageResponseDto(savedMessage, currentUser);
    }

    @Override
    @Transactional
    public DirectMessageResponseDto updateMessage(UserEntity currentUser, Long messageId, UpdateDirectMessageRequestDto request) {
        DirectMessageEntity message = directMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        if (!message.getSender().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the message author can edit this message");
        }

        if (message.getDeletedAt() != null) {
            throw new IllegalStateException("Cannot edit a deleted message");
        }

        message.setContent(request.getContent());
        message.setEdited(true);

        DirectMessageEntity updatedMessage = directMessageRepository.save(message);
        return mapToDirectMessageResponseDto(updatedMessage, currentUser);
    }

    @Override
    @Transactional
    public void deleteMessage(UserEntity currentUser, Long messageId) {
        DirectMessageEntity message = directMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));

        if (!message.getSender().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the message author can delete this message");
        }

        if (message.getDeletedAt() == null) {
            message.setDeletedAt(Instant.now());
            directMessageRepository.save(message);
        }
    }

    @Override
    @Transactional
    public void markThreadAsRead(UserEntity currentUser, Long threadId) {
        DirectMessageThreadEntity thread = getThreadAndVerifyParticipant(threadId, currentUser);
        DirectMessageReadStateEntity readState = directMessageReadStateRepository
                .findByThread_IdAndUser_Id(thread.getId(), currentUser.getId())
                .orElseGet(() -> DirectMessageReadStateEntity.builder()
                        .thread(thread)
                        .user(currentUser)
                        .build());

        Instant now = Instant.now();
        readState.setLastReadAt(now);
        directMessageReadStateRepository.save(readState);

        if (isReadReceiptsEnabled(currentUser.getId()) && directMessageReadReceiptRepository != null) {
            List<DirectMessageEntity> messages = directMessageRepository.findByThread_Id(thread.getId());
            for (DirectMessageEntity m : messages) {
                if (m.getDeletedAt() == null && !directMessageReadReceiptRepository.existsByMessage_IdAndUser_Id(m.getId(), currentUser.getId())) {
                    directMessageReadReceiptRepository.save(DirectMessageReadReceiptEntity.builder()
                            .message(m)
                            .user(currentUser)
                            .readAt(now)
                            .build());
                }
            }
        }

        if (eventPublisher != null) {
            UserEntity recipient = thread.getUser1().getId().equals(currentUser.getId()) ? thread.getUser2() : thread.getUser1();
            if (recipient != null) {
                eventPublisher.publishEvent(DirectMessageReadReceiptEvent.builder()
                        .threadId(thread.getId())
                        .readerUserId(currentUser.getId())
                        .recipientUserId(recipient.getId())
                        .readAt(now)
                        .build());
            }
        }
    }

    @Override
    public UserMessagePrivacySettingsDto getPrivacySettings(UserEntity currentUser) {
        UserMessagePrivacySettingsEntity entity = userMessagePrivacySettingsRepository.findByUser_Id(currentUser.getId())
                .orElseGet(() -> UserMessagePrivacySettingsEntity.builder()
                        .user(currentUser)
                        .allowInternalDms(true)
                        .allowExternalDms(true)
                        .readReceiptsEnabled(true)
                        .build());

        return DirectMessageMapper.toUserMessagePrivacySettingsDto(entity);
    }

    @Override
    @Transactional
    public UserMessagePrivacySettingsDto updatePrivacySettings(UserEntity currentUser, UserMessagePrivacySettingsDto dto) {
        UserMessagePrivacySettingsEntity entity = userMessagePrivacySettingsRepository.findByUser_Id(currentUser.getId())
                .orElseGet(() -> UserMessagePrivacySettingsEntity.builder()
                        .user(currentUser)
                        .build());

        entity.setAllowInternalDms(dto.isAllowInternalDms());
        entity.setAllowExternalDms(dto.isAllowExternalDms());
        entity.setReadReceiptsEnabled(dto.isReadReceiptsEnabled());

        UserMessagePrivacySettingsEntity saved = userMessagePrivacySettingsRepository.save(entity);
        return DirectMessageMapper.toUserMessagePrivacySettingsDto(saved);
    }

    @Override
    @Transactional
    public UserBlockResponseDto blockUser(UserEntity currentUser, String targetUserId) {
        if (currentUser.getId().equalsIgnoreCase(targetUserId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }

        UserEntity targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found: " + targetUserId));

        UserBlockEntity block = userBlockRepository.findByBlockingUser_IdAndBlockedUser_Id(currentUser.getId(), targetUserId)
                .orElseGet(() -> userBlockRepository.save(
                        UserBlockEntity.builder()
                                .blockingUser(currentUser)
                                .blockedUser(targetUser)
                                .build()
                ));

        return DirectMessageMapper.toUserBlockResponseDto(block);
    }

    @Override
    @Transactional
    public void unblockUser(UserEntity currentUser, String targetUserId) {
        userBlockRepository.deleteByBlockingUser_IdAndBlockedUser_Id(currentUser.getId(), targetUserId);
    }

    @Override
    public List<UserBlockResponseDto> getBlockedUsers(UserEntity currentUser) {
        List<UserBlockEntity> blocks = userBlockRepository.findByBlockingUser_Id(currentUser.getId());
        return blocks.stream()
                .map(DirectMessageMapper::toUserBlockResponseDto)
                .toList();
    }

    private UserEntity resolveTargetUser(InitiateThreadRequestDto request) {
        if (request.getTargetUserId() != null && !request.getTargetUserId().isBlank()) {
            return userRepository.findById(request.getTargetUserId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + request.getTargetUserId()));
        }
        if (request.getTargetEmail() != null && !request.getTargetEmail().isBlank()) {
            return userRepository.findByEmail(request.getTargetEmail())
                    .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + request.getTargetEmail()));
        }
        throw new IllegalArgumentException("Target user ID or email must be provided");
    }

    private void validateCommunicationEligibility(UserEntity sender, UserEntity recipient) {
        // Block check
        if (userBlockRepository.existsByBlockingUser_IdAndBlockedUser_Id(sender.getId(), recipient.getId())
                || userBlockRepository.existsByBlockingUser_IdAndBlockedUser_Id(recipient.getId(), sender.getId())) {
            throw new AccessDeniedException("Communication blocked between users");
        }

        String senderOrgCode = sender.getOrganization() != null ? sender.getOrganization().getOrgCode() : null;
        String recipientOrgCode = recipient.getOrganization() != null ? recipient.getOrganization().getOrgCode() : null;

        boolean isExternal = senderOrgCode != null && recipientOrgCode != null && !senderOrgCode.equalsIgnoreCase(recipientOrgCode);

        // Privacy settings check
        UserMessagePrivacySettingsEntity privacy = userMessagePrivacySettingsRepository.findByUser_Id(recipient.getId()).orElse(null);
        if (privacy != null) {
            if (isExternal && !privacy.isAllowExternalDms()) {
                throw new AccessDeniedException("User does not accept external direct messages");
            }
            if (!isExternal && !privacy.isAllowInternalDms()) {
                throw new AccessDeniedException("User does not accept internal direct messages");
            }
        }

        // External cross-org trust / collaboration check
        if (isExternal) {
            boolean hasTrust = orgTrustRelationshipService != null && orgTrustRelationshipService.hasActiveTrust(senderOrgCode, recipientOrgCode);
            boolean hasCollab = isCrossOrgCollaborationActive(senderOrgCode, recipientOrgCode);
            if (!hasTrust && !hasCollab) {
                throw new AccessDeniedException("External direct messaging requires active trust relationship or active collaboration between organizations");
            }
        }
    }

    private boolean isCrossOrgCollaborationActive(String orgCodeA, String orgCodeB) {
        if (orgCodeA == null || orgCodeB == null || collaborationRepository == null) {
            return false;
        }
        List<CollaborationEntity> collabs = collaborationRepository.findByOwnerOrganization_OrgCodeOrPartnerOrganization_OrgCode(orgCodeA, orgCodeA);
        return collabs.stream().anyMatch(c -> c.getStatus() == CollaborationStatus.ACTIVE &&
                ((c.getOwnerOrganization() != null && c.getOwnerOrganization().getOrgCode().equalsIgnoreCase(orgCodeB)) ||
                 (c.getPartnerOrganization() != null && c.getPartnerOrganization().getOrgCode().equalsIgnoreCase(orgCodeB))));
    }

    private DirectMessageThreadEntity getThreadAndVerifyParticipant(Long threadId, UserEntity user) {
        DirectMessageThreadEntity thread = directMessageThreadRepository.findById(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Thread not found: " + threadId));

        if (!thread.getUser1().getId().equals(user.getId()) && !thread.getUser2().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not authorized to access this thread");
        }
        return thread;
    }

    private boolean isReadReceiptsEnabled(String userId) {
        if (userId == null) return true;
        return userMessagePrivacySettingsRepository.findByUser_Id(userId)
                .map(UserMessagePrivacySettingsEntity::isReadReceiptsEnabled)
                .orElse(true);
    }

    private DirectMessageResponseDto mapToDirectMessageResponseDto(DirectMessageEntity message, UserEntity currentUser) {
        if (message == null) return null;
        List<DirectMessageReadReceiptEntity> receipts = directMessageReadReceiptRepository != null
                ? directMessageReadReceiptRepository.findByMessage_Id(message.getId())
                : List.of();

        List<DirectMessageReadReceiptEntity> validReceipts = receipts.stream()
                .filter(r -> r.getUser() != null && isReadReceiptsEnabled(r.getUser().getId()))
                .toList();

        return DirectMessageMapper.toDirectMessageResponseDto(
                message,
                validReceipts,
                currentUser != null ? currentUser.getId() : null,
                true
        );
    }

    private DirectMessageThreadResponseDto buildThreadResponseDto(DirectMessageThreadEntity thread, UserEntity currentUser) {
        UserEntity otherParticipant = thread.getUser1().getId().equals(currentUser.getId()) ? thread.getUser2() : thread.getUser1();
        DirectMessageEntity lastMessageEntity = directMessageRepository.findTopByThread_IdOrderByCreatedAtDesc(thread.getId()).orElse(null);
        DirectMessageResponseDto lastMessageDto = mapToDirectMessageResponseDto(lastMessageEntity, currentUser);

        Optional<DirectMessageReadStateEntity> readState = directMessageReadStateRepository.findByThread_IdAndUser_Id(thread.getId(), currentUser.getId());
        long unreadCount;
        if (readState.isPresent()) {
            unreadCount = directMessageRepository.countByThread_IdAndCreatedAtAfterAndDeletedAtIsNullAndSender_IdNot(
                    thread.getId(), readState.get().getLastReadAt(), currentUser.getId());
        } else {
            unreadCount = directMessageRepository.countByThread_IdAndDeletedAtIsNullAndSender_IdNot(
                    thread.getId(), currentUser.getId());
        }

        return DirectMessageMapper.toDirectMessageThreadResponseDto(thread, otherParticipant, lastMessageDto, unreadCount);
    }
}
