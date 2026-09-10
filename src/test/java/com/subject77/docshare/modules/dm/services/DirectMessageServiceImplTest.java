package com.subject77.docshare.modules.dm.services;

import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.dm.dto.*;
import com.subject77.docshare.modules.dm.entities.*;
import com.subject77.docshare.modules.dm.repository.*;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceImplTest {

    @Mock
    private DirectMessageThreadRepository threadRepository;

    @Mock
    private DirectMessageRepository messageRepository;

    @Mock
    private DirectMessageReadStateRepository readStateRepository;

    @Mock
    private UserMessagePrivacySettingsRepository privacySettingsRepository;

    @Mock
    private UserBlockRepository userBlockRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrgTrustRelationshipService orgTrustRelationshipService;

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DirectMessageServiceImpl directMessageService;

    private UserEntity user1;
    private UserEntity user2;
    private UserEntity user3External;
    private OrganizationEntity org1;
    private OrganizationEntity org2;

    private DirectMessageThreadEntity thread1;

    @BeforeEach
    void setUp() {
        org1 = OrganizationEntity.builder()
                .orgCode("ORG1")
                .name("Organization 1")
                .build();

        org2 = OrganizationEntity.builder()
                .orgCode("ORG2")
                .name("Organization 2")
                .build();

        user1 = UserEntity.builder()
                .id("usr-1")
                .email("user1@org1.com")
                .firstName("User")
                .lastName("One")
                .organization(org1)
                .build();

        user2 = UserEntity.builder()
                .id("usr-2")
                .email("user2@org1.com")
                .firstName("User")
                .lastName("Two")
                .organization(org1)
                .build();

        user3External = UserEntity.builder()
                .id("usr-3")
                .email("user3@org2.com")
                .firstName("User")
                .lastName("Three")
                .organization(org2)
                .build();

        thread1 = DirectMessageThreadEntity.builder()
                .id(100L)
                .user1(user1)
                .user2(user2)
                .lastMessageAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Initiate thread - internal user success")
    void initiateOrGetThread_InternalSuccess() {
        InitiateThreadRequestDto request = InitiateThreadRequestDto.builder()
                .recipientUserId("usr-2")
                .build();

        when(userRepository.findById("usr-2")).thenReturn(Optional.of(user2));
        when(userBlockRepository.existsByBlockingUser_IdAndBlockedUser_Id("usr-1", "usr-2")).thenReturn(false);
        when(userBlockRepository.existsByBlockingUser_IdAndBlockedUser_Id("usr-2", "usr-1")).thenReturn(false);
        when(privacySettingsRepository.findByUser_Id("usr-2")).thenReturn(Optional.empty());
        when(threadRepository.findThreadBetweenUsers("usr-1", "usr-2")).thenReturn(Optional.empty());
        when(threadRepository.save(any())).thenAnswer(invocation -> {
            DirectMessageThreadEntity t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });

        DirectMessageThreadResponseDto response = directMessageService.initiateOrGetThread(user1, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getParticipant()).isNotNull();
        assertThat(response.getParticipant().getId()).isEqualTo("usr-2");
    }

    @Test
    @DisplayName("Initiate thread - self DM throws IllegalArgumentException")
    void initiateOrGetThread_SelfDmFails() {
        InitiateThreadRequestDto request = InitiateThreadRequestDto.builder()
                .recipientUserId("usr-1")
                .build();

        when(userRepository.findById("usr-1")).thenReturn(Optional.of(user1));

        assertThatThrownBy(() -> directMessageService.initiateOrGetThread(user1, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot send direct messages to yourself");
    }

    @Test
    @DisplayName("Initiate thread - blocked user throws AccessDeniedException")
    void initiateOrGetThread_BlockedUser() {
        InitiateThreadRequestDto request = InitiateThreadRequestDto.builder()
                .recipientUserId("usr-2")
                .build();

        when(userRepository.findById("usr-2")).thenReturn(Optional.of(user2));
        when(userBlockRepository.existsByBlockingUser_IdAndBlockedUser_Id("usr-1", "usr-2")).thenReturn(true);

        assertThatThrownBy(() -> directMessageService.initiateOrGetThread(user1, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Communication blocked between users");
    }

    @Test
    @DisplayName("Initiate thread - external user without trust/collaboration throws AccessDeniedException")
    void initiateOrGetThread_ExternalNoTrust() {
        InitiateThreadRequestDto request = InitiateThreadRequestDto.builder()
                .recipientUserId("usr-3")
                .build();

        when(userRepository.findById("usr-3")).thenReturn(Optional.of(user3External));
        when(userBlockRepository.existsByBlockingUser_IdAndBlockedUser_Id("usr-1", "usr-3")).thenReturn(false);
        when(userBlockRepository.existsByBlockingUser_IdAndBlockedUser_Id("usr-3", "usr-1")).thenReturn(false);
        when(privacySettingsRepository.findByUser_Id("usr-3")).thenReturn(Optional.empty());
        when(orgTrustRelationshipService.hasActiveTrust("ORG1", "ORG2")).thenReturn(false);
        when(collaborationRepository.findByOwnerOrganization_OrgCodeOrPartnerOrganization_OrgCode("ORG1", "ORG1")).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> directMessageService.initiateOrGetThread(user1, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("External direct messaging requires active trust relationship or active collaboration");
    }

    @Test
    @DisplayName("Get user threads - returns paginated threads")
    void getUserThreads_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<DirectMessageThreadEntity> threadPage = new PageImpl<>(List.of(thread1), pageable, 1);

        when(threadRepository.findThreadsByUserId("usr-1", pageable)).thenReturn(threadPage);

        DirectMessageThreadPageResponseDto response = directMessageService.getUserThreads(user1, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getThreads()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Get single thread - success for participant")
    void getThread_Success() {
        when(threadRepository.findById(100L)).thenReturn(Optional.of(thread1));
        when(readStateRepository.findByThread_IdAndUser_Id(100L, "usr-1")).thenReturn(Optional.of(DirectMessageReadStateEntity.builder().lastReadAt(Instant.now()).build()));
        when(messageRepository.countByThread_IdAndCreatedAtAfterAndDeletedAtIsNullAndSender_IdNot(eq(100L), any(), eq("usr-1"))).thenReturn(2L);

        DirectMessageThreadResponseDto response = directMessageService.getThread(user1, 100L);

        assertThat(response).isNotNull();
        assertThat(response.getUnreadCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Get single thread - throws AccessDeniedException if not participant")
    void getThread_NotParticipant() {
        when(threadRepository.findById(100L)).thenReturn(Optional.of(thread1));

        assertThatThrownBy(() -> directMessageService.getThread(user3External, 100L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Not authorized to access this thread");
    }

    @Test
    @DisplayName("Send message - success")
    void sendMessage_Success() {
        CreateDirectMessageRequestDto request = CreateDirectMessageRequestDto.builder()
                .content("Hello User Two!")
                .build();

        when(threadRepository.findById(100L)).thenReturn(Optional.of(thread1));
        when(userBlockRepository.existsByBlockingUser_IdAndBlockedUser_Id("usr-1", "usr-2")).thenReturn(false);
        when(userBlockRepository.existsByBlockingUser_IdAndBlockedUser_Id("usr-2", "usr-1")).thenReturn(false);
        when(privacySettingsRepository.findByUser_Id("usr-2")).thenReturn(Optional.empty());

        DirectMessageEntity savedMsg = DirectMessageEntity.builder()
                .id(500L)
                .thread(thread1)
                .sender(user1)
                .content("Hello User Two!")
                .createdAt(Instant.now())
                .edited(false)
                .build();
        when(messageRepository.save(any())).thenReturn(savedMsg);

        DirectMessageResponseDto response = directMessageService.sendMessage(user1, 100L, request);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("Hello User Two!");
        verify(threadRepository).save(thread1);
        verify(notificationService).createNotification(
                eq(user2),
                eq(user1),
                eq(NotificationType.DM_RECEIVED),
                eq("New Direct Message"),
                contains("User One sent you a direct message"),
                eq(NotificationSourceType.DM),
                eq("100"),
                isNull(),
                isNull(),
                eq("DM_RECEIVED:100:500:usr-2")
        );
    }

    @Test
    @DisplayName("Update message - success for sender")
    void updateMessage_Success() {
        DirectMessageEntity existingMsg = DirectMessageEntity.builder()
                .id(500L)
                .thread(thread1)
                .sender(user1)
                .content("Original content")
                .edited(false)
                .build();

        UpdateDirectMessageRequestDto request = UpdateDirectMessageRequestDto.builder()
                .content("Updated content")
                .build();

        when(messageRepository.findById(500L)).thenReturn(Optional.of(existingMsg));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DirectMessageResponseDto response = directMessageService.updateMessage(user1, 500L, request);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("Updated content");
        assertThat(response.isEdited()).isTrue();
        verify(messageRepository).save(existingMsg);
    }

    @Test
    @DisplayName("Update message - throws AccessDeniedException if not sender")
    void updateMessage_NotSender() {
        DirectMessageEntity existingMsg = DirectMessageEntity.builder()
                .id(500L)
                .thread(thread1)
                .sender(user1)
                .content("Original content")
                .edited(false)
                .build();

        UpdateDirectMessageRequestDto request = UpdateDirectMessageRequestDto.builder()
                .content("Updated content")
                .build();

        when(messageRepository.findById(500L)).thenReturn(Optional.of(existingMsg));

        assertThatThrownBy(() -> directMessageService.updateMessage(user2, 500L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the message author can edit this message");
    }

    @Test
    @DisplayName("Delete message - success for sender soft delete")
    void deleteMessage_Success() {
        DirectMessageEntity existingMsg = DirectMessageEntity.builder()
                .id(500L)
                .thread(thread1)
                .sender(user1)
                .content("Original content")
                .edited(false)
                .build();

        when(messageRepository.findById(500L)).thenReturn(Optional.of(existingMsg));

        directMessageService.deleteMessage(user1, 500L);

        assertThat(existingMsg.getDeletedAt()).isNotNull();
        verify(messageRepository).save(existingMsg);
    }

    @Test
    @DisplayName("Mark thread as read - updates user read state")
    void markThreadAsRead_Success() {
        when(threadRepository.findById(100L)).thenReturn(Optional.of(thread1));
        when(readStateRepository.findByThread_IdAndUser_Id(100L, "usr-1")).thenReturn(Optional.empty());

        directMessageService.markThreadAsRead(user1, 100L);

        verify(readStateRepository).save(any(DirectMessageReadStateEntity.class));
        verify(eventPublisher).publishEvent(any(com.subject77.docshare.modules.communication.events.DirectMessageReadReceiptEvent.class));
    }

    @Test
    @DisplayName("Block user - success")
    void blockUser_Success() {
        when(userRepository.findById("usr-2")).thenReturn(Optional.of(user2));
        when(userBlockRepository.findByBlockingUser_IdAndBlockedUser_Id("usr-1", "usr-2")).thenReturn(Optional.empty());

        UserBlockEntity savedBlock = UserBlockEntity.builder()
                .id(10L)
                .blockingUser(user1)
                .blockedUser(user2)
                .createdAt(Instant.now())
                .build();
        when(userBlockRepository.save(any())).thenReturn(savedBlock);

        UserBlockResponseDto response = directMessageService.blockUser(user1, "usr-2");

        assertThat(response).isNotNull();
        assertThat(response.getBlockedUser()).isNotNull();
        assertThat(response.getBlockedUser().getId()).isEqualTo("usr-2");
    }

    @Test
    @DisplayName("Unblock user - success")
    void unblockUser_Success() {
        directMessageService.unblockUser(user1, "usr-2");

        verify(userBlockRepository).deleteByBlockingUser_IdAndBlockedUser_Id("usr-1", "usr-2");
    }

    @Test
    @DisplayName("Privacy settings - update and retrieve settings")
    void privacySettings_GetAndUpdate() {
        UserMessagePrivacySettingsEntity settings = UserMessagePrivacySettingsEntity.builder()
                .id(1L)
                .user(user1)
                .allowInternalDms(true)
                .allowExternalDms(false)
                .build();

        when(privacySettingsRepository.findByUser_Id("usr-1")).thenReturn(Optional.of(settings));

        UserMessagePrivacySettingsDto result = directMessageService.getPrivacySettings(user1);
        assertThat(result.isAllowExternalDms()).isFalse();

        UserMessagePrivacySettingsDto updateReq = UserMessagePrivacySettingsDto.builder()
                .allowInternalDms(true)
                .allowExternalDms(true)
                .build();
        when(privacySettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserMessagePrivacySettingsDto updatedResult = directMessageService.updatePrivacySettings(user1, updateReq);
        assertThat(updatedResult.isAllowExternalDms()).isTrue();
    }
}
