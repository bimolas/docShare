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
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.subject77.docshare.modules.channel.repository.MessageAttachmentRepository;
import com.subject77.docshare.modules.channel.repository.MessageReadReceiptRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.document.services.StorageService;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ChannelMessageServiceImplTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ChannelMemberRepository channelMemberRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageReactionRepository messageReactionRepository;

    @Mock
    private MessageAttachmentRepository messageAttachmentRepository;

    @Mock
    private MessageReadReceiptRepository messageReadReceiptRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private PermissionEvaluatorService permissionEvaluatorService;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ChannelReadStateRepository channelReadStateRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChannelMessageServiceImpl channelMessageService;

    private UserEntity author;
    private UserEntity otherUser;
    private UserEntity adminUser;
    private OrganizationEntity organization;
    private ChannelEntity standardChannel;
    private ChannelEntity announcementChannel;
    private ChannelEntity archivedChannel;

    @BeforeEach
    void setUp() {
        author = UserEntity.builder()
                .id("user-author-123")
                .email("author@example.com")
                .firstName("Author")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        otherUser = UserEntity.builder()
                .id("user-other-456")
                .email("other@example.com")
                .firstName("Other")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        adminUser = UserEntity.builder()
                .id("user-admin-789")
                .email("admin@example.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        organization = OrganizationEntity.builder()
                .orgCode("ORG100")
                .name("Acme Corp")
                .build();

        standardChannel = ChannelEntity.builder()
                .id(1L)
                .name("general")
                .type(ChannelType.STANDARD)
                .status(ChannelStatus.ACTIVE)
                .organization(organization)
                .build();

        announcementChannel = ChannelEntity.builder()
                .id(2L)
                .name("announcements")
                .type(ChannelType.ANNOUNCEMENT)
                .status(ChannelStatus.ACTIVE)
                .organization(organization)
                .build();

        archivedChannel = ChannelEntity.builder()
                .id(3L)
                .name("old-project")
                .type(ChannelType.STANDARD)
                .status(ChannelStatus.ARCHIVED)
                .organization(organization)
                .build();
    }

    @Nested
    @DisplayName("Create Message Tests")
    class CreateMessageTests {

        @Test
        @DisplayName("createMessage_success_standardChannel")
        void createMessage_success_standardChannel() {
            CreateMessageRequestDto request = CreateMessageRequestDto.builder()
                    .content("Hello World!")
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
                MessageEntity m = invocation.getArgument(0);
                m.setId(10L);
                m.setCreatedAt(Instant.now());
                m.setUpdatedAt(Instant.now());
                return m;
            });

            MessageResponseDto result = channelMessageService.createMessage(1L, request, author);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getContent()).isEqualTo("Hello World!");
            assertThat(result.getAuthorId()).isEqualTo(author.getId());
            assertThat(result.getChannelId()).isEqualTo(1L);
            assertThat(result.isDeleted()).isFalse();

            verify(messageRepository).save(any(MessageEntity.class));
        }

        @Test
        @DisplayName("createMessage_fails_blankContent")
        void createMessage_fails_blankContent() {
            CreateMessageRequestDto request = CreateMessageRequestDto.builder()
                    .content("   ")
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);

            assertThatThrownBy(() -> channelMessageService.createMessage(1L, request, author))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Message content cannot be blank");
        }

        @Test
        @DisplayName("createMessage_fails_archivedChannel")
        void createMessage_fails_archivedChannel() {
            CreateMessageRequestDto request = CreateMessageRequestDto.builder()
                    .content("Posting to archived")
                    .build();

            when(channelRepository.findById(3L)).thenReturn(Optional.of(archivedChannel));

            assertThatThrownBy(() -> channelMessageService.createMessage(3L, request, author))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("archived");
        }

        @Test
        @DisplayName("createMessage_announcementChannel_denied_regularMember")
        void createMessage_announcementChannel_denied_regularMember() {
            CreateMessageRequestDto request = CreateMessageRequestDto.builder()
                    .content("Unauthorized Announcement")
                    .build();

            when(channelRepository.findById(2L)).thenReturn(Optional.of(announcementChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(2L, author.getId())).thenReturn(true);
            
            ChannelMemberEntity member = ChannelMemberEntity.builder()
                    .channel(announcementChannel)
                    .user(author)
                    .role(ChannelMemberRole.MEMBER)
                    .build();
            when(channelMemberRepository.findByChannel_IdAndUser_Id(2L, author.getId())).thenReturn(Optional.of(member));

            assertThatThrownBy(() -> channelMessageService.createMessage(2L, request, author))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("Only channel administrators and moderators can post to announcement channels");
        }

        @Test
        @DisplayName("createMessage_announcementChannel_allowed_moderatorOrAdmin")
        void createMessage_announcementChannel_allowed_moderatorOrAdmin() {
            CreateMessageRequestDto request = CreateMessageRequestDto.builder()
                    .content("Official Announcement")
                    .build();

            when(channelRepository.findById(2L)).thenReturn(Optional.of(announcementChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(2L, author.getId())).thenReturn(true);

            ChannelMemberEntity member = ChannelMemberEntity.builder()
                    .channel(announcementChannel)
                    .user(author)
                    .role(ChannelMemberRole.MODERATOR)
                    .build();
            when(channelMemberRepository.findByChannel_IdAndUser_Id(2L, author.getId())).thenReturn(Optional.of(member));

            when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
                MessageEntity m = invocation.getArgument(0);
                m.setId(20L);
                m.setCreatedAt(Instant.now());
                m.setUpdatedAt(Instant.now());
                return m;
            });

            MessageResponseDto result = channelMessageService.createMessage(2L, request, author);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(20L);
            assertThat(result.getContent()).isEqualTo("Official Announcement");
        }

        @Test
        @DisplayName("createMessage_threadReply_success_validParent")
        void createMessage_threadReply_success_validParent() {
            MessageEntity parentMessage = MessageEntity.builder()
                    .id(50L)
                    .channel(standardChannel)
                    .author(otherUser)
                    .content("Root Question")
                    .build();

            CreateMessageRequestDto request = CreateMessageRequestDto.builder()
                    .content("Thread Answer")
                    .parentMessageId(50L)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(50L)).thenReturn(Optional.of(parentMessage));

            when(messageRepository.save(any(MessageEntity.class))).thenAnswer(invocation -> {
                MessageEntity m = invocation.getArgument(0);
                m.setId(51L);
                m.setCreatedAt(Instant.now());
                m.setUpdatedAt(Instant.now());
                return m;
            });

            MessageResponseDto result = channelMessageService.createMessage(1L, request, author);

            assertThat(result).isNotNull();
            assertThat(result.getParentMessageId()).isEqualTo(50L);
            assertThat(result.getContent()).isEqualTo("Thread Answer");
        }

        @Test
        @DisplayName("createMessage_threadReply_fails_parentBelongsToDifferentChannel")
        void createMessage_threadReply_fails_parentBelongsToDifferentChannel() {
            MessageEntity otherChannelParent = MessageEntity.builder()
                    .id(99L)
                    .channel(announcementChannel)
                    .author(otherUser)
                    .content("Announcement post")
                    .build();

            CreateMessageRequestDto request = CreateMessageRequestDto.builder()
                    .content("Cross channel reply attempt")
                    .parentMessageId(99L)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(99L)).thenReturn(Optional.of(otherChannelParent));

            assertThatThrownBy(() -> channelMessageService.createMessage(1L, request, author))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Parent message does not belong to channel ID: 1");
        }
    }

    @Nested
    @DisplayName("Get Messages Tests")
    class GetMessagesTests {

        @Test
        @DisplayName("getMessages_rootMessages_paginated")
        void getMessages_rootMessages_paginated() {
            MessageEntity msg1 = MessageEntity.builder()
                    .id(1L)
                    .channel(standardChannel)
                    .author(author)
                    .content("First post")
                    .createdAt(Instant.now())
                    .build();

            Page<MessageEntity> page = new PageImpl<>(List.of(msg1), PageRequest.of(0, 10), 1);

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findByChannel_IdAndParentMessageIsNull(eq(1L), any(Pageable.class))).thenReturn(page);

            MessagePageResponseDto response = channelMessageService.getMessages(1L, null, PageRequest.of(0, 10), author);

            assertThat(response).isNotNull();
            assertThat(response.getMessages()).hasSize(1);
            assertThat(response.getMessages().get(0).getContent()).isEqualTo("First post");
            assertThat(response.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("getMessages_threadReplies_paginated")
        void getMessages_threadReplies_paginated() {
            MessageEntity reply1 = MessageEntity.builder()
                    .id(2L)
                    .channel(standardChannel)
                    .author(otherUser)
                    .content("Reply post")
                    .createdAt(Instant.now())
                    .build();

            Page<MessageEntity> page = new PageImpl<>(List.of(reply1), PageRequest.of(0, 10), 1);

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findByChannel_IdAndParentMessage_Id(eq(1L), eq(100L), any(Pageable.class))).thenReturn(page);

            MessagePageResponseDto response = channelMessageService.getMessages(1L, 100L, PageRequest.of(0, 10), author);

            assertThat(response).isNotNull();
            assertThat(response.getMessages()).hasSize(1);
            assertThat(response.getMessages().get(0).getContent()).isEqualTo("Reply post");
        }
    }

    @Nested
    @DisplayName("Update Message Tests")
    class UpdateMessageTests {

        @Test
        @DisplayName("updateMessage_success_author")
        void updateMessage_success_author() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Original text")
                    .edited(false)
                    .build();

            UpdateMessageRequestDto request = UpdateMessageRequestDto.builder()
                    .content("Updated text")
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
            when(messageRepository.save(any(MessageEntity.class))).thenAnswer(i -> i.getArgument(0));

            MessageResponseDto response = channelMessageService.updateMessage(1L, 5L, request, author);

            assertThat(response).isNotNull();
            assertThat(response.getContent()).isEqualTo("Updated text");
            assertThat(response.isEdited()).isTrue();
        }

        @Test
        @DisplayName("updateMessage_fails_nonAuthorNonAdmin")
        void updateMessage_fails_nonAuthorNonAdmin() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Original text")
                    .edited(false)
                    .build();

            UpdateMessageRequestDto request = UpdateMessageRequestDto.builder()
                    .content("Attempted hack update")
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, otherUser.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));

            assertThatThrownBy(() -> channelMessageService.updateMessage(1L, 5L, request, otherUser))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("User is not authorized to edit or delete this message");
        }

        @Test
        @DisplayName("updateMessage_fails_deletedMessage")
        void updateMessage_fails_deletedMessage() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Original text")
                    .deletedAt(Instant.now())
                    .build();

            UpdateMessageRequestDto request = UpdateMessageRequestDto.builder()
                    .content("Updating soft deleted")
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));

            assertThatThrownBy(() -> channelMessageService.updateMessage(1L, 5L, request, author))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Deleted messages cannot be edited");
        }
    }

    @Nested
    @DisplayName("Delete Message Tests")
    class DeleteMessageTests {

        @Test
        @DisplayName("deleteMessage_softDelete_retainsRecord")
        void deleteMessage_softDelete_retainsRecord() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Content to delete")
                    .deletedAt(null)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
            when(messageRepository.save(any(MessageEntity.class))).thenAnswer(i -> i.getArgument(0));

            MessageResponseDto response = channelMessageService.deleteMessage(1L, 5L, author);

            assertThat(response).isNotNull();
            assertThat(response.isDeleted()).isTrue();
            assertThat(response.getDeletedAt()).isNotNull();
            assertThat(response.getContent()).isEqualTo("[This message was deleted]");

            verify(messageRepository).save(argThat(m -> m.getDeletedAt() != null));
        }
    }

    @Nested
    @DisplayName("Reaction Tests")
    class ReactionTests {

        @Test
        @DisplayName("addReaction_success_newReaction")
        void addReaction_success_newReaction() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Great news")
                    .build();

            AddMessageReactionRequestDto request = AddMessageReactionRequestDto.builder()
                    .reaction("👍")
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
            when(messageReactionRepository.existsByMessage_IdAndUser_IdAndReaction(5L, author.getId(), "👍")).thenReturn(false);

            when(messageReactionRepository.save(any(MessageReactionEntity.class))).thenAnswer(i -> {
                MessageReactionEntity r = i.getArgument(0);
                r.setId(100L);
                r.setCreatedAt(Instant.now());
                return r;
            });

            MessageReactionResponseDto response = channelMessageService.addReaction(1L, 5L, request, author);

            assertThat(response).isNotNull();
            assertThat(response.getReaction()).isEqualTo("👍");
            assertThat(response.getUserId()).isEqualTo(author.getId());
            assertThat(response.getMessageId()).isEqualTo(5L);
        }

        @Test
        @DisplayName("addReaction_fails_duplicateReaction")
        void addReaction_fails_duplicateReaction() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Great news")
                    .build();

            AddMessageReactionRequestDto request = AddMessageReactionRequestDto.builder()
                    .reaction("👍")
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
            when(messageReactionRepository.existsByMessage_IdAndUser_IdAndReaction(5L, author.getId(), "👍")).thenReturn(true);

            assertThatThrownBy(() -> channelMessageService.addReaction(1L, 5L, request, author))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User has already added reaction");
        }

        @Test
        @DisplayName("removeReaction_success")
        void removeReaction_success() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Great news")
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));

            channelMessageService.removeReaction(1L, 5L, "👍", author);

            verify(messageReactionRepository).deleteByMessage_IdAndUser_IdAndReaction(5L, author.getId(), "👍");
        }
    }

    @Nested
    @DisplayName("Attachment Tests")
    class AttachmentTests {

        @Test
        @DisplayName("addAttachmentToMessage_success")
        void addAttachmentToMessage_success() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Check this document")
                    .build();

            DocumentEntity document = DocumentEntity.builder()
                    .id(100L)
                    .fileName("report.pdf")
                    .contentType("application/pdf")
                    .fileSize(1024L)
                    .objectKey("orgs/1/docs/report.pdf")
                    .status(DocumentStatus.ACTIVE)
                    .build();

            MessageAttachmentEntity savedAttachment = MessageAttachmentEntity.builder()
                    .id(10L)
                    .message(message)
                    .document(document)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
            when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
            when(permissionEvaluatorService.canViewDocument(author, document)).thenReturn(true);
            when(permissionEvaluatorService.canDownloadDocument(author, document)).thenReturn(true);
            when(messageAttachmentRepository.existsByMessage_IdAndDocument_Id(5L, 100L)).thenReturn(false);
            when(messageAttachmentRepository.save(any(MessageAttachmentEntity.class))).thenReturn(savedAttachment);
            when(messageAttachmentRepository.findByMessage_Id(5L)).thenReturn(List.of(savedAttachment));
            when(storageService.generatePresignedDownloadUrl("orgs/1/docs/report.pdf", "report.pdf", 60L))
                    .thenReturn("https://s3.amazonaws.com/presigned-url");

            MessageResponseDto response = channelMessageService.addAttachmentToMessage(1L, 5L, 100L, author);

            assertThat(response).isNotNull();
            assertThat(response.getAttachments()).hasSize(1);
            MessageAttachmentResponseDto attDto = response.getAttachments().get(0);
            assertThat(attDto.getAttachmentId()).isEqualTo(10L);
            assertThat(attDto.getDocumentId()).isEqualTo(100L);
            assertThat(attDto.getFileName()).isEqualTo("report.pdf");
            assertThat(attDto.isAccessGranted()).isTrue();
            assertThat(attDto.isDownloadable()).isTrue();
            assertThat(attDto.getDownloadUrl()).isEqualTo("https://s3.amazonaws.com/presigned-url");
            assertThat(attDto.getAccessRestrictionReason()).isNull();

            verify(messageAttachmentRepository).save(any(MessageAttachmentEntity.class));
        }

        @Test
        @DisplayName("addAttachmentToMessage_fails_accessDenied")
        void addAttachmentToMessage_fails_accessDenied() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
            .channel(standardChannel)
                    .author(author)
                    .content("Attaching unauthorized doc")
                    .build();

            DocumentEntity document = DocumentEntity.builder()
                    .id(101L)
                    .fileName("restricted.pdf")
                    .status(DocumentStatus.ACTIVE)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
            when(documentRepository.findById(101L)).thenReturn(Optional.of(document));
            when(permissionEvaluatorService.canViewDocument(author, document)).thenReturn(false);

            assertThatThrownBy(() -> channelMessageService.addAttachmentToMessage(1L, 5L, 101L, author))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have permission to attach document ID: 101");
        }

        @Test
        @DisplayName("addAttachmentToMessage_fails_deletedMessage")
        void addAttachmentToMessage_fails_deletedMessage() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Deleted message")
                    .deletedAt(Instant.now())
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));

            assertThatThrownBy(() -> channelMessageService.addAttachmentToMessage(1L, 5L, 100L, author))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot add attachments to deleted messages");
        }

        @Test
        @DisplayName("removeAttachmentFromMessage_success")
        void removeAttachmentFromMessage_success() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Message with attachment")
                    .build();

            DocumentEntity document = DocumentEntity.builder()
                    .id(100L)
                    .fileName("report.pdf")
                    .build();

            MessageAttachmentEntity attachment = MessageAttachmentEntity.builder()
                    .id(10L)
                    .message(message)
                    .document(document)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
            when(messageAttachmentRepository.findById(10L)).thenReturn(Optional.of(attachment));
            when(messageAttachmentRepository.findByMessage_Id(5L)).thenReturn(Collections.emptyList());

            MessageResponseDto response = channelMessageService.removeAttachmentFromMessage(1L, 5L, 10L, author);

            assertThat(response).isNotNull();
            assertThat(response.getAttachments()).isEmpty();

            verify(messageAttachmentRepository).delete(attachment);
        }

        @Test
        @DisplayName("removeAttachmentFromMessage_fails_wrongMessage")
        void removeAttachmentFromMessage_fails_wrongMessage() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Message 5")
                    .build();

            MessageEntity otherMessage = MessageEntity.builder()
                    .id(99L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Message 99")
                    .build();

            MessageAttachmentEntity attachment = MessageAttachmentEntity.builder()
                    .id(10L)
                    .message(otherMessage)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
            when(messageAttachmentRepository.findById(10L)).thenReturn(Optional.of(attachment));

            assertThatThrownBy(() -> channelMessageService.removeAttachmentFromMessage(1L, 5L, 10L, author))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Attachment ID 10 does not belong to message ID 5");
        }

        @Test
        @DisplayName("attachmentMapping_userWithoutViewPermission_restrictsAccess")
        void attachmentMapping_userWithoutViewPermission_restrictsAccess() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Shared message with restricted attachment")
                    .build();

            DocumentEntity document = DocumentEntity.builder()
                    .id(200L)
                    .fileName("confidential.docx")
                    .status(DocumentStatus.ACTIVE)
                    .build();

            MessageAttachmentEntity attachment = MessageAttachmentEntity.builder()
                    .id(15L)
                    .message(message)
                    .document(document)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, otherUser.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
            when(messageAttachmentRepository.findByMessage_Id(5L)).thenReturn(List.of(attachment));
            when(permissionEvaluatorService.canViewDocument(otherUser, document)).thenReturn(false);

            MessageResponseDto response = channelMessageService.getMessage(1L, 5L, otherUser);

            assertThat(response).isNotNull();
            assertThat(response.getAttachments()).hasSize(1);
            MessageAttachmentResponseDto attDto = response.getAttachments().get(0);
            assertThat(attDto.isAccessGranted()).isFalse();
            assertThat(attDto.isDownloadable()).isFalse();
            assertThat(attDto.getDownloadUrl()).isNull();
            assertThat(attDto.getAccessRestrictionReason()).isEqualTo("Insufficient permissions to access attached document");
        }

        @Test
        @DisplayName("attachmentMapping_inactiveDocument_preventsDownload")
        void attachmentMapping_inactiveDocument_preventsDownload() {
            MessageEntity message = MessageEntity.builder()
                    .id(5L)
                    .channel(standardChannel)
                    .author(author)
                    .content("Message with inactive doc")
                    .build();

            DocumentEntity pendingDoc = DocumentEntity.builder()
                    .id(300L)
                    .fileName("old_spec.pdf")
                    .status(DocumentStatus.PENDING_UPLOAD)
                    .build();

            MessageAttachmentEntity attachment = MessageAttachmentEntity.builder()
                    .id(20L)
                    .message(message)
                    .document(pendingDoc)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
            when(messageRepository.findById(5L)).thenReturn(Optional.of(message));
            when(messageAttachmentRepository.findByMessage_Id(5L)).thenReturn(List.of(attachment));
            when(permissionEvaluatorService.canViewDocument(author, pendingDoc)).thenReturn(true);
            when(permissionEvaluatorService.canDownloadDocument(author, pendingDoc)).thenReturn(true);

            MessageResponseDto response = channelMessageService.getMessage(1L, 5L, author);

            assertThat(response).isNotNull();
            assertThat(response.getAttachments()).hasSize(1);
            MessageAttachmentResponseDto attDto = response.getAttachments().get(0);
            assertThat(attDto.isAccessGranted()).isTrue();
            assertThat(attDto.isDownloadable()).isFalse();
            assertThat(attDto.getDownloadUrl()).isNull();
            assertThat(attDto.getAccessRestrictionReason()).isEqualTo("Document is not active");
        }
    }

    @Test
    @DisplayName("markChannelAsRead should save read state and publish ChannelReadReceiptEvent")
    void markChannelAsRead_ShouldSaveStateAndPublishEvent() {
        MessageEntity latestMessage = MessageEntity.builder()
                .id(10L)
                .channel(standardChannel)
                .author(otherUser)
                .content("Hello")
                .createdAt(Instant.now())
                .build();

        when(channelRepository.findById(1L)).thenReturn(Optional.of(standardChannel));
        when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(true);
        when(channelReadStateRepository.findByChannel_IdAndUser_Id(1L, author.getId())).thenReturn(Optional.empty());
        when(messageRepository.findByChannel_IdAndDeletedAtIsNull(1L)).thenReturn(List.of(latestMessage));
        when(messageReadReceiptRepository.existsByMessage_IdAndUser_Id(10L, author.getId())).thenReturn(false);

        channelMessageService.markChannelAsRead(1L, author);

        verify(channelReadStateRepository).save(any(ChannelReadStateEntity.class));
        verify(messageReadReceiptRepository).save(any(MessageReadReceiptEntity.class));
        verify(eventPublisher).publishEvent(any(com.subject77.docshare.modules.communication.events.ChannelReadReceiptEvent.class));
    }
}
