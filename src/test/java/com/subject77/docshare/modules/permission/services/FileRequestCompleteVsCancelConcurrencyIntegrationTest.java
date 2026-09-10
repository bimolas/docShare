package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.InvalidDocumentStateException;
import com.subject77.docshare.modules.audit.entities.AccessLogEntity;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.repository.AccessLogRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.repository.NotificationRepository;
import com.subject77.docshare.modules.permission.dto.FileRequestResponseDto;
import com.subject77.docshare.modules.permission.entities.FileRequestEntity;
import com.subject77.docshare.modules.permission.entities.FileRequestRecipientType;
import com.subject77.docshare.modules.permission.entities.FileRequestStatus;
import com.subject77.docshare.modules.permission.repository.FileRequestRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/docshare",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driverClassName=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.datasource.hikari.maximum-pool-size=25"
})
public class FileRequestCompleteVsCancelConcurrencyIntegrationTest {

    @Autowired
    private FileRequestService fileRequestService;

    @Autowired
    private FileRequestRepository fileRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private AccessLogRepository accessLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    @Test
    @DisplayName("Complete vs Cancel race: exactly one terminal transition wins from PENDING")
    void completeVsCancel_concurrentRace_onlyOneWins() throws Exception {
        String uid1 = "fr-req-" + UUID.randomUUID().toString().substring(0, 8);
        String uid2 = "fr-rec-" + UUID.randomUUID().toString().substring(0, 8);

        UserEntity requester = userRepository.save(UserEntity.builder()
                .id(uid1)
                .email(uid1 + "@example.com")
                .firstName("Requester")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        UserEntity recipient = userRepository.save(UserEntity.builder()
                .id(uid2)
                .email(uid2 + "@example.com")
                .firstName("Recipient")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        FolderEntity folder = folderRepository.save(FolderEntity.builder()
                .name("Uploads-" + UUID.randomUUID().toString().substring(0, 6))
                .createdBy(requester)
                .build());

        FileRequestEntity fr = fileRequestRepository.save(FileRequestEntity.builder()
                .requester(requester)
                .recipientType(FileRequestRecipientType.USER)
                .recipientUser(recipient)
                .destinationFolder(folder)
                .title("Annual Audit Docs")
                .status(FileRequestStatus.PENDING)
                .build());

        Long requestId = fr.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicReference<FileRequestResponseDto> completeSuccess = new AtomicReference<>();
        AtomicReference<Throwable> completeFailure = new AtomicReference<>();
        AtomicReference<FileRequestResponseDto> cancelSuccess = new AtomicReference<>();
        AtomicReference<Throwable> cancelFailure = new AtomicReference<>();

        Future<?> futureComplete = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                FileRequestResponseDto dto = fileRequestService.completeFileRequest(requestId, recipient);
                completeSuccess.set(dto);
            } catch (Throwable t) {
                completeFailure.set(t);
            }
        });

        Future<?> futureCancel = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                FileRequestResponseDto dto = fileRequestService.cancelFileRequest(requestId, requester);
                cancelSuccess.set(dto);
            } catch (Throwable t) {
                cancelFailure.set(t);
            }
        });

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        futureComplete.get(10, TimeUnit.SECONDS);
        futureCancel.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one succeeded and one failed
        boolean completeWon = completeSuccess.get() != null;
        boolean cancelWon = cancelSuccess.get() != null;

        assertThat(completeWon ^ cancelWon)
                .as("Exactly one of complete or cancel must succeed (completeWon=%s, cancelWon=%s)", completeWon, cancelWon)
                .isTrue();

        if (completeWon) {
            assertThat(completeSuccess.get().getStatus()).isEqualTo(FileRequestStatus.FULFILLED);
            assertThat(cancelFailure.get()).isNotNull();
            assertThat(cancelFailure.get()).isInstanceOf(InvalidDocumentStateException.class);
        } else {
            assertThat(cancelSuccess.get().getStatus()).isEqualTo(FileRequestStatus.CANCELLED);
            assertThat(completeFailure.get()).isNotNull();
            assertThat(completeFailure.get()).isInstanceOf(InvalidDocumentStateException.class);
        }

        // Database entity reflects the winning status
        FileRequestEntity finalEntity = fileRequestRepository.findById(requestId).orElseThrow();
        FileRequestStatus expectedFinalStatus = completeWon ? FileRequestStatus.FULFILLED : FileRequestStatus.CANCELLED;
        assertThat(finalEntity.getStatus()).isEqualTo(expectedFinalStatus);

        // Audit integrity: exactly 1 log entry exists for this completion/cancellation
        List<AccessLogEntity> logs = accessLogRepository.findByFolder_IdOrderByTimestampDesc(folder.getId());
        long lifecycleLogCount = logs.stream()
                .filter(l -> l.getAction() == Action.FILE_REQUEST_FULFILLED || l.getAction() == Action.FILE_REQUEST_CANCELLED)
                .count();
        assertThat(lifecycleLogCount)
                .as("Only the winning transition should have produced an audit entry")
                .isEqualTo(1);

        // Notification integrity: exactly 1 notification exists for this terminal transition
        List<NotificationEntity> notifications = notificationRepository.findAll().stream()
                .filter(n -> ("FILE_REQUEST_FULFILLED:" + requestId + ":" + requester.getId()).equals(n.getIdempotencyKey())
                        || ("FILE_REQUEST_CANCELLED:" + requestId + ":" + recipient.getId()).equals(n.getIdempotencyKey()))
                .toList();
        assertThat(notifications)
                .as("Only the winning transition should have sent a notification")
                .hasSize(1);
    }
}
