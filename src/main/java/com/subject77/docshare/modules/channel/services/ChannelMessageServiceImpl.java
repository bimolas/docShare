package com.subject77.docshare.modules.channel.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.channel.dto.*;
import com.subject77.docshare.modules.channel.entities.*;
import com.subject77.docshare.modules.channel.repository.ChannelMemberRepository;
import com.subject77.docshare.modules.channel.repository.ChannelReadStateRepository;
import com.subject77.docshare.modules.channel.repository.ChannelRepository;
import com.subject77.docshare.modules.channel.repository.MessageReactionRepository;
import com.subject77.docshare.modules.channel.repository.MessageRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.document.services.StorageService;
import com.subject77.docshare.modules.channel.repository.MessageAttachmentRepository;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.communication.events.ChannelReadReceiptEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.subject77.docshare.modules.channel.repository.MessageReadReceiptRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelMessageServiceImpl implements ChannelMessageService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final MessageRepository messageRepository;
    private final MessageReactionRepository messageReactionRepository;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final MessageReadReceiptRepository messageReadReceiptRepository;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final UserOrganizationRepository userOrganizationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ChannelReadStateRepository channelReadStateRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public MessageResponseDto createMessage(Long channelId, CreateMessageRequestDto request, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        ensureChannelActive(channel);
        verifyCanPostToChannel(channel, currentUser);

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be blank");
        }

        MessageEntity parentMessage = null;
        if (request.getParentMessageId() != null) {
            parentMessage = messageRepository.findById(request.getParentMessageId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent message not found with ID: " + request.getParentMessageId()));

            if (!parentMessage.getChannel().getId().equals(channelId)) {
                throw new IllegalArgumentException("Parent message does not belong to channel ID: " + channelId);
            }

            // Flatten to 1 parent level if parent is already a reply
            if (parentMessage.getParentMessage() != null) {
                parentMessage = parentMessage.getParentMessage();
            }
        }

        MessageEntity message = MessageEntity.builder()
                .channel(channel)
                .author(currentUser)
                .content(request.getContent().trim())
                .parentMessage(parentMessage)
                .edited(false)
                .build();

        MessageEntity saved = messageRepository.save(message);

        if (request.getAttachmentDocumentIds() != null && !request.getAttachmentDocumentIds().isEmpty()) {
            for (Long docId : request.getAttachmentDocumentIds()) {
                DocumentEntity doc = documentRepository.findById(docId)
                        .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + docId));

                if (!permissionEvaluatorService.canViewDocument(currentUser, doc)) {
                    throw new AccessDeniedException("User does not have permission to attach document ID: " + docId);
                }

                if (!messageAttachmentRepository.existsByMessage_IdAndDocument_Id(saved.getId(), docId)) {
                    MessageAttachmentEntity attachment = MessageAttachmentEntity.builder()
                            .message(saved)
                            .document(doc)
                            .build();
                    messageAttachmentRepository.save(attachment);
                }
            }
        }

        log.info("Message ID {} created in channel ID {} by user {}", saved.getId(), channelId, currentUser.getId());

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "MESSAGE_CREATED",
                    "CHANNEL_MESSAGE",
                    saved.getId(),
                    null,
                    null,
                    null,
                    channel.getOrganization() != null ? channel.getOrganization().getOrgCode() : null,
                    currentUser,
                    currentUser.getEmail(),
                    null,
                    null,
                    null,
                    null
            );
        }

        if (notificationService != null) {
            if (parentMessage != null && parentMessage.getAuthor() != null) {
                UserEntity threadAuthor = parentMessage.getAuthor();
                if (!threadAuthor.getId().equalsIgnoreCase(currentUser.getId())) {
                    try {
                        String replyKey = "CHANNEL_THREAD_REPLY:" + channel.getId() + ":" + saved.getId() + ":" + threadAuthor.getId();
                        notificationService.createNotification(
                                threadAuthor,
                                currentUser,
                                NotificationType.CHANNEL_THREAD_REPLY,
                                "New Thread Reply",
                                String.format("Replied to your thread in #%s", channel.getName()),
                                NotificationSourceType.CHANNEL,
                                String.valueOf(channel.getId()),
                                null,
                                null,
                                replyKey
                        );
                    } catch (Exception e) {
                        log.error("Failed to send CHANNEL_THREAD_REPLY notification to user {}: {}", threadAuthor.getId(), e.getMessage());
                    }
                }
            }

            processMessageMentions(saved.getContent(), channel, currentUser, saved.getId());
        }

        if (!messageReadReceiptRepository.existsByMessage_IdAndUser_Id(saved.getId(), currentUser.getId())) {
            messageReadReceiptRepository.save(MessageReadReceiptEntity.builder()
                    .message(saved)
                    .user(currentUser)
                    .readAt(saved.getCreatedAt())
                    .build());
        }

        return mapToMessageResponseDto(saved, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public MessagePageResponseDto getMessages(Long channelId, Long parentMessageId, Pageable pageable, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        verifyAccessToChannel(channel, currentUser);

        // Standardize sorting: default to createdAt ASC if unspecified
        Pageable effectivePageable = pageable;
        if (pageable.getSort().isUnsorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.ASC, "createdAt"));
        }

        Page<MessageEntity> messagePage;
        if (parentMessageId != null) {
            messagePage = messageRepository.findByChannel_IdAndParentMessage_Id(channelId, parentMessageId, effectivePageable);
        } else {
            messagePage = messageRepository.findByChannel_IdAndParentMessageIsNull(channelId, effectivePageable);
        }

        List<MessageResponseDto> dtos = messagePage.getContent().stream()
                .map(m -> mapToMessageResponseDto(m, currentUser))
                .collect(Collectors.toList());

        return MessagePageResponseDto.builder()
                .messages(dtos)
                .pageNumber(messagePage.getNumber())
                .pageSize(messagePage.getSize())
                .totalElements(messagePage.getTotalElements())
                .totalPages(messagePage.getTotalPages())
                .hasNext(messagePage.hasNext())
                .hasPrevious(messagePage.hasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MessageResponseDto getMessage(Long channelId, Long messageId, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        verifyAccessToChannel(channel, currentUser);

        MessageEntity message = findMessageById(messageId);
        if (!message.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("Message ID " + messageId + " does not belong to channel ID " + channelId);
        }

        return mapToMessageResponseDto(message, currentUser);
    }

    @Override
    @Transactional
    public MessageResponseDto updateMessage(Long channelId, Long messageId, UpdateMessageRequestDto request, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        ensureChannelActive(channel);
        verifyAccessToChannel(channel, currentUser);

        MessageEntity message = findMessageById(messageId);
        if (!message.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("Message ID " + messageId + " does not belong to channel ID " + channelId);
        }

        if (message.getDeletedAt() != null) {
            throw new IllegalStateException("Deleted messages cannot be edited");
        }

        verifyCanModifyMessage(message, currentUser);

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Updated message content cannot be blank");
        }

        message.setContent(request.getContent().trim());
        message.setEdited(true);

        MessageEntity updated = messageRepository.save(message);

        log.info("Message ID {} updated in channel ID {} by user {}", updated.getId(), channelId, currentUser.getId());

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "MESSAGE_EDITED",
                    "CHANNEL_MESSAGE",
                    updated.getId(),
                    null,
                    null,
                    null,
                    channel.getOrganization() != null ? channel.getOrganization().getOrgCode() : null,
                    currentUser,
                    currentUser.getEmail(),
                    null,
                    null,
                    null,
                    null
            );
        }

        return mapToMessageResponseDto(updated, currentUser);
    }

    @Override
    @Transactional
    public MessageResponseDto deleteMessage(Long channelId, Long messageId, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        verifyAccessToChannel(channel, currentUser);

        MessageEntity message = findMessageById(messageId);
        if (!message.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("Message ID " + messageId + " does not belong to channel ID " + channelId);
        }

        verifyCanModifyMessage(message, currentUser);

        if (message.getDeletedAt() == null) {
            message.setDeletedAt(Instant.now());
            messageRepository.save(message);

            log.info("Message ID {} soft-deleted in channel ID {} by user {}", message.getId(), channelId, currentUser.getId());

            if (auditLogService != null) {
                auditLogService.logPermissionChange(
                        "MESSAGE_DELETED",
                        "CHANNEL_MESSAGE",
                        message.getId(),
                        null,
                        null,
                        null,
                        channel.getOrganization() != null ? channel.getOrganization().getOrgCode() : null,
                        currentUser,
                        currentUser.getEmail(),
                        null,
                        null,
                        null,
                        null
                );
            }
        }

        return mapToMessageResponseDto(message, currentUser);
    }

    @Override
    @Transactional
    public MessageReactionResponseDto addReaction(Long channelId, Long messageId, AddMessageReactionRequestDto request, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        ensureChannelActive(channel);
        verifyAccessToChannel(channel, currentUser);

        MessageEntity message = findMessageById(messageId);
        if (!message.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("Message ID " + messageId + " does not belong to channel ID " + channelId);
        }

        if (message.getDeletedAt() != null) {
            throw new IllegalStateException("Cannot add reactions to deleted messages");
        }

        if (request.getReaction() == null || request.getReaction().trim().isEmpty()) {
            throw new IllegalArgumentException("Reaction string cannot be blank");
        }

        String normalizedReaction = request.getReaction().trim().toLowerCase();

        boolean exists = messageReactionRepository.existsByMessage_IdAndUser_IdAndReaction(messageId, currentUser.getId(), normalizedReaction);
        if (exists) {
            throw new IllegalArgumentException("User has already added reaction '" + normalizedReaction + "' to message ID " + messageId);
        }

        MessageReactionEntity reactionEntity = MessageReactionEntity.builder()
                .message(message)
                .user(currentUser)
                .reaction(normalizedReaction)
                .build();

        MessageReactionEntity saved = messageReactionRepository.save(reactionEntity);

        return MessageReactionResponseDto.builder()
                .id(saved.getId())
                .messageId(message.getId())
                .userId(currentUser.getId())
                .userName(currentUser.getFirstName() + " " + currentUser.getLastName())
                .reaction(saved.getReaction())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public void removeReaction(Long channelId, Long messageId, String reaction, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        verifyAccessToChannel(channel, currentUser);

        MessageEntity message = findMessageById(messageId);
        if (!message.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("Message ID " + messageId + " does not belong to channel ID " + channelId);
        }

        if (reaction == null || reaction.trim().isEmpty()) {
            throw new IllegalArgumentException("Reaction string cannot be blank");
        }

        String normalizedReaction = reaction.trim().toLowerCase();
        messageReactionRepository.deleteByMessage_IdAndUser_IdAndReaction(messageId, currentUser.getId(), normalizedReaction);
    }

    @Override
    @Transactional
    public MessageResponseDto addAttachmentToMessage(Long channelId, Long messageId, Long documentId, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        ensureChannelActive(channel);
        verifyAccessToChannel(channel, currentUser);

        MessageEntity message = findMessageById(messageId);
        if (!message.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("Message ID " + messageId + " does not belong to channel ID " + channelId);
        }

        if (message.getDeletedAt() != null) {
            throw new IllegalStateException("Cannot add attachments to deleted messages");
        }

        verifyCanModifyMessage(message, currentUser);

        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!permissionEvaluatorService.canViewDocument(currentUser, doc)) {
            throw new AccessDeniedException("User does not have permission to attach document ID: " + documentId);
        }

        if (!messageAttachmentRepository.existsByMessage_IdAndDocument_Id(messageId, documentId)) {
            MessageAttachmentEntity attachment = MessageAttachmentEntity.builder()
                    .message(message)
                    .document(doc)
                    .build();
            messageAttachmentRepository.save(attachment);
        }

        return mapToMessageResponseDto(message, currentUser);
    }

    @Override
    @Transactional
    public MessageResponseDto removeAttachmentFromMessage(Long channelId, Long messageId, Long attachmentId, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        verifyAccessToChannel(channel, currentUser);

        MessageEntity message = findMessageById(messageId);
        if (!message.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("Message ID " + messageId + " does not belong to channel ID " + channelId);
        }

        if (message.getDeletedAt() != null) {
            throw new IllegalStateException("Cannot modify deleted messages");
        }

        verifyCanModifyMessage(message, currentUser);

        MessageAttachmentEntity attachment = messageAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Message attachment not found with ID: " + attachmentId));

        if (!attachment.getMessage().getId().equals(messageId)) {
            throw new IllegalArgumentException("Attachment ID " + attachmentId + " does not belong to message ID " + messageId);
        }

        messageAttachmentRepository.delete(attachment);

        return mapToMessageResponseDto(message, currentUser);
    }

    // Helper Methods

    private ChannelEntity findChannelById(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found with ID: " + channelId));
    }

    private MessageEntity findMessageById(Long messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));
    }

    private void ensureChannelActive(ChannelEntity channel) {
        if (channel.getStatus() == ChannelStatus.ARCHIVED) {
            throw new IllegalStateException("Channel is archived and cannot receive new messages or replies");
        }
    }

    private void verifyAccessToChannel(ChannelEntity channel, UserEntity user) {
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) return;

        boolean isMember = channelMemberRepository.existsByChannel_IdAndUser_Id(channel.getId(), user.getId());
        if (isMember) return;

        if (channel.getType() == ChannelType.STANDARD || channel.getType() == ChannelType.ANNOUNCEMENT) {
            if (channel.getOrganization() != null && userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode(user.getId(), channel.getOrganization().getOrgCode())) {
                return;
            }
        }

        throw new AccessDeniedException("Access denied to channel ID: " + channel.getId());
    }

    private void verifyCanPostToChannel(ChannelEntity channel, UserEntity user) {
        verifyAccessToChannel(channel, user);

        if (channel.getType() == ChannelType.ANNOUNCEMENT) {
            if (user.getRole() == Role.ROLE_SUPER_ADMIN) return;

            Optional<ChannelMemberEntity> memberOpt = channelMemberRepository.findByChannel_IdAndUser_Id(channel.getId(), user.getId());
            if (memberOpt.isPresent()) {
                ChannelMemberRole role = memberOpt.get().getRole();
                if (role == ChannelMemberRole.ADMIN || role == ChannelMemberRole.MODERATOR || role == ChannelMemberRole.MANAGER) {
                    return;
                }
            }

            if (channel.getOrganization() != null) {
                Optional<UserOrganizationEntity> uo = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(user.getId(), channel.getOrganization().getOrgCode());
                if (uo.isPresent() && (uo.get().getRole() == Role.ROLE_ORG_ADMIN || uo.get().getRole() == Role.ROLE_ORG_MANAGER)) {
                    return;
                }
            }

            throw new AccessDeniedException("Only channel administrators and moderators can post to announcement channels");
        }
    }

    private void verifyCanModifyMessage(MessageEntity message, UserEntity user) {
        if (message.getAuthor().getId().equals(user.getId())) return;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) return;

        Optional<ChannelMemberEntity> memberOpt = channelMemberRepository.findByChannel_IdAndUser_Id(message.getChannel().getId(), user.getId());
        if (memberOpt.isPresent()) {
            ChannelMemberRole role = memberOpt.get().getRole();
            if (role == ChannelMemberRole.ADMIN || role == ChannelMemberRole.MODERATOR || role == ChannelMemberRole.MANAGER) {
                return;
            }
        }

        if (message.getChannel().getOrganization() != null) {
            Optional<UserOrganizationEntity> uo = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(user.getId(), message.getChannel().getOrganization().getOrgCode());
            if (uo.isPresent() && (uo.get().getRole() == Role.ROLE_ORG_ADMIN || uo.get().getRole() == Role.ROLE_ORG_MANAGER)) {
                return;
            }
        }

        throw new AccessDeniedException("User is not authorized to edit or delete this message");
    }

    private MessageResponseDto mapToMessageResponseDto(MessageEntity message, UserEntity currentUser) {
        boolean isDeleted = message.getDeletedAt() != null;
        String displayContent = isDeleted ? "[This message was deleted]" : message.getContent();

        List<MessageReactionEntity> reactions = messageReactionRepository.findByMessage_Id(message.getId());
        List<MessageReactionResponseDto> reactionDtos = reactions.stream()
                .map(r -> MessageReactionResponseDto.builder()
                        .id(r.getId())
                        .messageId(message.getId())
                        .userId(r.getUser().getId())
                        .userName(r.getUser().getFirstName() + " " + r.getUser().getLastName())
                        .reaction(r.getReaction())
                        .createdAt(r.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        List<MessageAttachmentEntity> attachments = messageAttachmentRepository.findByMessage_Id(message.getId());
        List<MessageAttachmentResponseDto> attachmentDtos = attachments.stream()
                .map(att -> mapToMessageAttachmentResponseDto(att, currentUser))
                .collect(Collectors.toList());

        List<MessageReadReceiptEntity> readReceiptEntities = messageReadReceiptRepository.findByMessage_Id(message.getId());
        List<MessageReadReceiptDto> readReceiptDtos = readReceiptEntities.stream()
                .map(r -> MessageReadReceiptDto.builder()
                        .userId(r.getUser() != null ? r.getUser().getId() : null)
                        .userName(r.getUser() != null && r.getUser().getFirstName() != null
                                ? r.getUser().getFirstName() + (r.getUser().getLastName() != null ? " " + r.getUser().getLastName() : "")
                                : (r.getUser() != null ? r.getUser().getEmail() : null))
                        .userEmail(r.getUser() != null ? r.getUser().getEmail() : null)
                        .readAt(r.getReadAt())
                        .build())
                .collect(Collectors.toList());

        long seenCount = readReceiptEntities.size();

        List<String> seenBy = readReceiptEntities.stream()
                .map(r -> {
                    UserEntity u = r.getUser();
                    if (u == null) return null;
                    if (u.getFirstName() != null && !u.getFirstName().isBlank()) {
                        return u.getFirstName() + (u.getLastName() != null && !u.getLastName().isBlank() ? " " + u.getLastName() : "");
                    }
                    return u.getEmail();
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        boolean readByCurrentUser = currentUser != null && (
                (message.getAuthor() != null && currentUser.getId().equals(message.getAuthor().getId())) ||
                readReceiptEntities.stream().anyMatch(r -> r.getUser() != null && currentUser.getId().equals(r.getUser().getId()))
        );

        long replyCount = messageRepository.countByParentMessage_Id(message.getId());

        UserEntity author = message.getAuthor();

        return MessageResponseDto.builder()
                .id(message.getId())
                .channelId(message.getChannel().getId())
                .authorId(author != null ? author.getId() : null)
                .authorName(author != null ? (author.getFirstName() + " " + author.getLastName()) : null)
                .authorEmail(author != null ? author.getEmail() : null)
                .content(displayContent)
                .parentMessageId(message.getParentMessage() != null ? message.getParentMessage().getId() : null)
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .deletedAt(message.getDeletedAt())
                .edited(message.isEdited())
                .deleted(isDeleted)
                .reactions(reactionDtos)
                .attachments(attachmentDtos)
                .replyCount(replyCount)
                .seenCount(seenCount)
                .seenBy(seenBy)
                .readReceipts(readReceiptDtos)
                .readByCurrentUser(readByCurrentUser)
                .build();
    }

    private MessageAttachmentResponseDto mapToMessageAttachmentResponseDto(MessageAttachmentEntity attachment, UserEntity currentUser) {
        DocumentEntity doc = attachment.getDocument();

        boolean accessGranted = currentUser != null && permissionEvaluatorService.canViewDocument(currentUser, doc);
        boolean canDownload = accessGranted && permissionEvaluatorService.canDownloadDocument(currentUser, doc);
        boolean isActive = doc.getStatus() == DocumentStatus.ACTIVE;

        boolean downloadable = canDownload && isActive;
        String downloadUrl = null;
        String restrictionReason = null;

        if (!accessGranted) {
            restrictionReason = "Insufficient permissions to access attached document";
        } else if (!isActive) {
            restrictionReason = "Document is not active";
        } else if (!canDownload) {
            restrictionReason = "Insufficient permissions to download document";
        } else {
            try {
                downloadUrl = storageService.generatePresignedDownloadUrl(doc.getObjectKey(), doc.getFileName(), 60L);
            } catch (Exception e) {
                log.warn("Failed to generate presigned download URL for document ID {}: {}", doc.getId(), e.getMessage());
            }
        }

        return MessageAttachmentResponseDto.builder()
                .attachmentId(attachment.getId())
                .documentId(doc.getId())
                .fileName(doc.getFileName())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .accessGranted(accessGranted)
                .downloadable(downloadable)
                .downloadUrl(downloadUrl)
                .accessRestrictionReason(restrictionReason)
                .build();
    }

    private void processMessageMentions(String content, ChannelEntity channel, UserEntity actor, Long messageId) {
        if (content == null || !content.contains("@") || notificationService == null) {
            return;
        }

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("@([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}|[a-zA-Z0-9._-]+)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        Set<String> notifiedUserIds = new HashSet<>();

        List<ChannelMemberEntity> members = channelMemberRepository.findByChannel_Id(channel.getId());

        while (matcher.find()) {
            String handle = matcher.group(1).toLowerCase();
            for (ChannelMemberEntity member : members) {
                UserEntity user = member.getUser();
                if (user == null || user.getId() == null) continue;
                if (user.getId().equalsIgnoreCase(actor.getId())) continue;

                boolean matches = user.getId().equalsIgnoreCase(handle)
                        || (user.getEmail() != null && user.getEmail().equalsIgnoreCase(handle))
                        || (user.getEmail() != null && user.getEmail().split("@")[0].equalsIgnoreCase(handle));

                if (!matches && userRepository != null) {
                    Optional<UserEntity> userOpt = userRepository.findById(handle);
                    if (userOpt.isEmpty()) {
                        userOpt = userRepository.findByEmail(handle);
                    }
                    if (userOpt.isPresent() && userOpt.get().getId().equalsIgnoreCase(user.getId())) {
                        matches = true;
                    }
                }

                if (matches && notifiedUserIds.add(user.getId())) {
                    try {
                        String mentionKey = "CHANNEL_MENTION:" + channel.getId() + ":" + messageId + ":" + user.getId();
                        notificationService.createNotification(
                                user,
                                actor,
                                NotificationType.CHANNEL_MENTION,
                                "Mentioned in Channel",
                                String.format("Mentioned you in #%s", channel.getName()),
                                NotificationSourceType.CHANNEL,
                                String.valueOf(channel.getId()),
                                null,
                                null,
                                mentionKey
                        );
                    } catch (Exception e) {
                        log.error("Failed to send CHANNEL_MENTION notification to user {}: {}", user.getId(), e.getMessage());
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Read-receipt operations
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    public void markChannelAsRead(Long channelId, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        ensureUserIsMember(channel, currentUser);

        ChannelReadStateEntity readState = channelReadStateRepository
                .findByChannel_IdAndUser_Id(channelId, currentUser.getId())
                .orElseGet(() -> ChannelReadStateEntity.builder()
                        .channel(channel)
                        .user(currentUser)
                        .build());

        Instant now = Instant.now();
        readState.setLastReadAt(now);
        channelReadStateRepository.save(readState);

        List<MessageEntity> messages = messageRepository.findByChannel_IdAndDeletedAtIsNull(channelId);
        for (MessageEntity m : messages) {
            if (!messageReadReceiptRepository.existsByMessage_IdAndUser_Id(m.getId(), currentUser.getId())) {
                messageReadReceiptRepository.save(MessageReadReceiptEntity.builder()
                        .message(m)
                        .user(currentUser)
                        .readAt(now)
                        .build());
            }
        }

        if (eventPublisher != null) {
            eventPublisher.publishEvent(ChannelReadReceiptEvent.builder()
                    .channelId(channelId)
                    .userId(currentUser.getId())
                    .readAt(now)
                    .build());
        }
    }

    @Override
    public long getChannelUnreadCount(Long channelId, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        ensureUserIsMember(channel, currentUser);

        return channelReadStateRepository
                .findByChannel_IdAndUser_Id(channelId, currentUser.getId())
                .map(rs -> messageRepository
                        .countByChannel_IdAndCreatedAtAfterAndDeletedAtIsNullAndAuthor_IdNot(
                                channelId, rs.getLastReadAt(), currentUser.getId()))
                .orElseGet(() -> messageRepository
                        .countByChannel_IdAndDeletedAtIsNullAndAuthor_IdNot(
                                channelId, currentUser.getId()));
    }

    /** Verify that {@code user} is a member of the channel; throws {@link AccessDeniedException} otherwise. */
    private void ensureUserIsMember(ChannelEntity channel, UserEntity user) {
        if (!channelMemberRepository.existsByChannel_IdAndUser_Id(channel.getId(), user.getId())) {
            throw new AccessDeniedException("User is not a member of channel ID: " + channel.getId());
        }
    }
}

