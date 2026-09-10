package com.subject77.docshare.modules.collaboration.scheduler;

import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.collaboration.services.CollaborationServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled component responsible for auto-expiring ACTIVE collaborations
 * whose {@code endsAt} timestamp has passed.
 *
 * <p>For each expired collaboration the full teardown pipeline is executed:
 * <ol>
 *   <li>All active collaboration-derived permissions are revoked via
 *       {@link CollaborationServiceImpl#performCollaborationTeardown}
 *       which delegates to {@code PermissionService.revokePermission} - keeping
 *       DB mutation, audit log, Kafka events, and Redis cache invalidation
 *       inside the existing centralized pipeline.</li>
 *   <li>Collaboration status is set to {@code EXPIRED}.</li>
 *   <li>A COLLABORATION_EXPIRED audit event is persisted.</li>
 * </ol>
 *
 * <p>The system user ID SYSTEM_SCHEDULER is used as the actor for
 * permission revocation audit records to clearly distinguish automated teardowns
 * from manual administrator actions.
 *
 * <p>The method is idempotent - running it multiple times for the same
 * collaboration has no additional effect after the first run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollaborationExpirationScheduler {

    /**
     * Synthetic actor ID recorded in all audit events triggered by this scheduler.
     * This allows audit queries to distinguish automated expiration from human revocations.
     */
    static final String SYSTEM_SCHEDULER_ACTOR = "SYSTEM_SCHEDULER";

    private final CollaborationRepository collaborationRepository;
    private final CollaborationServiceImpl collaborationServiceImpl;

    /**
     * Runs every 5 minutes, identifies ACTIVE collaborations where endsAt is before now,
     * and processes each through the full teardown pipeline.
     * Cron: every 5 minutes.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void expireElapsedCollaborations() {
        Instant now = Instant.now();
        List<CollaborationEntity> expired =
                collaborationRepository.findByStatusAndEndsAtBefore(CollaborationStatus.ACTIVE, now);

        if (expired.isEmpty()) {
            log.debug("Collaboration expiration check: no collaborations to expire at {}", now);
            return;
        }

        log.info("Collaboration expiration check: {} collaboration(s) to process at {}", expired.size(), now);

        for (CollaborationEntity collaboration : expired) {
            processExpiredCollaboration(collaboration);
        }
    }

    @Transactional
    public void processExpiredCollaboration(CollaborationEntity collaboration) {
        Long collaborationId = collaboration.getId();
        log.info("Processing expiration for collaboration ID: {} (endsAt: {})",
                collaborationId, collaboration.getEndsAt());

        try {
            CollaborationEntity lockedCollab = collaborationRepository.findByIdForUpdate(collaborationId)
                    .orElse(null);
            if (lockedCollab == null) {
                log.warn("Collaboration ID: {} not found under lock during expiration check", collaborationId);
                return;
            }

            if (lockedCollab.getStatus() != CollaborationStatus.ACTIVE) {
                log.info("Collaboration ID: {} is no longer ACTIVE (current status: {}) - skipping expiration",
                        collaborationId, lockedCollab.getStatus());
                return;
            }

            if (lockedCollab.getEndsAt() == null || lockedCollab.getEndsAt().isAfter(Instant.now())) {
                log.info("Collaboration ID: {} endsAt ({}) is not elapsed - skipping expiration",
                        collaborationId, lockedCollab.getEndsAt());
                return;
            }

            // Revoke all active collaboration-derived permissions via the centralized pipeline.
            // Each call to PermissionService.revokePermission produces its own PERMISSION_REVOKED
            // audit record - these constitute the complete audit trail for this expiration event.
            collaborationServiceImpl.performCollaborationTeardown(lockedCollab, SYSTEM_SCHEDULER_ACTOR);

            // Mark collaboration as EXPIRED
            lockedCollab.setStatus(CollaborationStatus.EXPIRED);
            collaborationRepository.save(lockedCollab);

            // Send notification for collaboration expiration
            collaborationServiceImpl.sendCollaborationExpiredNotification(lockedCollab);

            log.info("Collaboration ID: {} successfully expired at {}", collaborationId, Instant.now());

        } catch (Exception e) {
            log.error("Failed to expire collaboration ID: {} - error: {}", collaborationId, e.getMessage(), e);
        }
    }
}
