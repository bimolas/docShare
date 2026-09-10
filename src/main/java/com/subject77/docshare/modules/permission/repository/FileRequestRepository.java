package com.subject77.docshare.modules.permission.repository;

import com.subject77.docshare.modules.permission.entities.FileRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileRequestRepository extends JpaRepository<FileRequestEntity, Long> {

    // ── Atomic conditional updates ───────────────────────────────────────────

    @Modifying
    @Query("""
            UPDATE FileRequestEntity fr
            SET fr.status = com.subject77.docshare.modules.permission.entities.FileRequestStatus.FULFILLED,
                fr.fulfilledAt = :timestamp,
                fr.updatedAt = :timestamp
            WHERE fr.id = :id AND fr.status = com.subject77.docshare.modules.permission.entities.FileRequestStatus.PENDING
            """)
    int markFulfilledIfPending(@Param("id") Long id, @Param("timestamp") Instant timestamp);

    @Modifying
    @Query("""
            UPDATE FileRequestEntity fr
            SET fr.status = com.subject77.docshare.modules.permission.entities.FileRequestStatus.CANCELLED,
                fr.cancelledAt = :timestamp,
                fr.updatedAt = :timestamp
            WHERE fr.id = :id AND fr.status = com.subject77.docshare.modules.permission.entities.FileRequestStatus.PENDING
            """)
    int markCancelledIfPending(@Param("id") Long id, @Param("timestamp") Instant timestamp);


    // ── Requester queries ────────────────────────────────────────────────────

    List<FileRequestEntity> findByRequesterIdOrderByCreatedAtDesc(String requesterId);

    Optional<FileRequestEntity> findByIdAndRequesterId(Long id, String requesterId);

    // ── USER-recipient queries ───────────────────────────────────────────────

    List<FileRequestEntity> findByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId);

    Optional<FileRequestEntity> findByIdAndRecipientUserId(Long id, String recipientUserId);

    // ── TEAM-recipient queries ───────────────────────────────────────────────

    /**
     * Returns TEAM-type file requests where the caller's user belongs to the
     * recipient group, ordered newest-first.  Used to populate the "received"
     * inbox for team members.
     *
     * <p>Membership is resolved dynamically at query time.</p>
     */
    @Query("""
            SELECT fr FROM FileRequestEntity fr
            WHERE fr.recipientGroup.id IN (
                SELECT ug.group.id FROM UserGroupEntity ug
                WHERE ug.user.id = :userId
            )
            ORDER BY fr.createdAt DESC
            """)
    List<FileRequestEntity> findTeamRequestsForUser(@Param("userId") String userId);

    /**
     * Checks if a PENDING file request targeting {@code groupId} with the
     * given title already exists from the same requester — used for
     * deduplication only, not for security enforcement.
     */
    boolean existsByRecipientGroupIdAndRequesterId(Long groupId, String requesterId);
}
