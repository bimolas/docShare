package com.subject77.docshare.modules.collaboration.scheduler;

import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.collaboration.services.CollaborationServiceImpl;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollaborationExpirationSchedulerTest {

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private CollaborationServiceImpl collaborationServiceImpl;

    @InjectMocks
    private CollaborationExpirationScheduler scheduler;

    private OrganizationEntity ownerOrg;
    private OrganizationEntity partnerOrg;
    private FolderEntity rootFolder;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();

        ownerOrg = OrganizationEntity.builder()
                .orgCode("ORG_OWNER")
                .name("Owner Organization")
                .build();

        partnerOrg = OrganizationEntity.builder()
                .orgCode("ORG_PARTNER")
                .name("Partner Organization")
                .build();

        rootFolder = FolderEntity.builder()
                .id(100L)
                .name("Collab Root")
                .build();
    }

    private CollaborationEntity buildExpiredCollaboration(Long id) {
        return CollaborationEntity.builder()
                .id(id)
                .name("Expired Collab " + id)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .rootFolder(rootFolder)
                .status(CollaborationStatus.ACTIVE)
                .startsAt(now.minusSeconds(172800))
                .endsAt(now.minusSeconds(3600))   // already past
                .createdAt(now.minusSeconds(172800))
                .updatedAt(now.minusSeconds(3600))
                .build();
    }

    @Test
    @DisplayName("expireElapsedCollaborations: no expired collaborations — no action taken")
    void expireElapsedCollaborations_NoneExpired_NoAction() {
        when(collaborationRepository.findByStatusAndEndsAtBefore(eq(CollaborationStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.expireElapsedCollaborations();

        verify(collaborationServiceImpl, never()).performCollaborationTeardown(any(), any());
        verify(collaborationRepository, never()).save(any());
    }

    @Test
    @DisplayName("expireElapsedCollaborations: single expired collaboration is torn down and marked EXPIRED")
    void expireElapsedCollaborations_OneExpired_ProcessesCorrectly() {
        CollaborationEntity collab = buildExpiredCollaboration(1L);

        when(collaborationRepository.findByStatusAndEndsAtBefore(eq(CollaborationStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(collab));
        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collab));
        when(collaborationRepository.save(collab)).thenReturn(collab);

        scheduler.expireElapsedCollaborations();

        // Teardown must be called with the SYSTEM_SCHEDULER actor
        verify(collaborationServiceImpl).performCollaborationTeardown(
                collab, CollaborationExpirationScheduler.SYSTEM_SCHEDULER_ACTOR);

        // Status must be updated to EXPIRED and persisted
        ArgumentCaptor<CollaborationEntity> saveCaptor = ArgumentCaptor.forClass(CollaborationEntity.class);
        verify(collaborationRepository).save(saveCaptor.capture());
        assertThat(saveCaptor.getValue().getStatus()).isEqualTo(CollaborationStatus.EXPIRED);
    }

    @Test
    @DisplayName("expireElapsedCollaborations: multiple expired collaborations are all processed")
    void expireElapsedCollaborations_MultipleExpired_AllProcessed() {
        CollaborationEntity collab1 = buildExpiredCollaboration(1L);
        CollaborationEntity collab2 = buildExpiredCollaboration(2L);
        CollaborationEntity collab3 = buildExpiredCollaboration(3L);

        when(collaborationRepository.findByStatusAndEndsAtBefore(eq(CollaborationStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(collab1, collab2, collab3));
        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collab1));
        when(collaborationRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(collab2));
        when(collaborationRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(collab3));
        when(collaborationRepository.save(any(CollaborationEntity.class))).thenAnswer(i -> i.getArgument(0));

        scheduler.expireElapsedCollaborations();

        verify(collaborationServiceImpl, times(3))
                .performCollaborationTeardown(any(CollaborationEntity.class),
                        eq(CollaborationExpirationScheduler.SYSTEM_SCHEDULER_ACTOR));
        verify(collaborationRepository, times(3)).save(any(CollaborationEntity.class));

        assertThat(collab1.getStatus()).isEqualTo(CollaborationStatus.EXPIRED);
        assertThat(collab2.getStatus()).isEqualTo(CollaborationStatus.EXPIRED);
        assertThat(collab3.getStatus()).isEqualTo(CollaborationStatus.EXPIRED);
    }

    @Test
    @DisplayName("expireElapsedCollaborations: teardown failure for one collaboration does not block the others")
    void expireElapsedCollaborations_TeardownFailureOnOne_OthersStillProcessed() {
        CollaborationEntity failingCollab = buildExpiredCollaboration(10L);
        CollaborationEntity successCollab = buildExpiredCollaboration(11L);

        when(collaborationRepository.findByStatusAndEndsAtBefore(eq(CollaborationStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(failingCollab, successCollab));
        when(collaborationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(failingCollab));
        when(collaborationRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(successCollab));

        doThrow(new RuntimeException("Simulated teardown failure"))
                .when(collaborationServiceImpl)
                .performCollaborationTeardown(eq(failingCollab), any());
        doNothing()
                .when(collaborationServiceImpl)
                .performCollaborationTeardown(eq(successCollab), any());
        when(collaborationRepository.save(successCollab)).thenReturn(successCollab);

        // Scheduler must NOT propagate the exception
        assertThatCode(() -> scheduler.expireElapsedCollaborations())
                .doesNotThrowAnyException();

        // failingCollab: teardown was attempted but save was not reached
        verify(collaborationRepository, never()).save(failingCollab);
        // successCollab: fully processed
        verify(collaborationRepository).save(successCollab);
        assertThat(successCollab.getStatus()).isEqualTo(CollaborationStatus.EXPIRED);
    }

    @Test
    @DisplayName("expireElapsedCollaborations: scheduler query uses ACTIVE status and current instant")
    void expireElapsedCollaborations_UsesActiveStatusAndCurrentInstant() {
        when(collaborationRepository.findByStatusAndEndsAtBefore(eq(CollaborationStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of());

        Instant before = Instant.now();
        scheduler.expireElapsedCollaborations();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(collaborationRepository).findByStatusAndEndsAtBefore(
                eq(CollaborationStatus.ACTIVE), instantCaptor.capture());

        Instant usedInstant = instantCaptor.getValue();
        assertThat(usedInstant).isAfterOrEqualTo(before.minusSeconds(1))
                               .isBeforeOrEqualTo(after.plusSeconds(1));
    }

    @Test
    @DisplayName("SYSTEM_SCHEDULER_ACTOR constant is a stable, identifiable string")
    void systemSchedulerActorConstant_IsStable() {
        assertThat(CollaborationExpirationScheduler.SYSTEM_SCHEDULER_ACTOR)
                .isNotBlank()
                .isEqualTo("SYSTEM_SCHEDULER");
    }
}
