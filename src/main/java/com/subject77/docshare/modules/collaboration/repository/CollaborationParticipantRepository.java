package com.subject77.docshare.modules.collaboration.repository;

import com.subject77.docshare.modules.collaboration.entities.CollaborationParticipantEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollaborationParticipantRepository extends JpaRepository<CollaborationParticipantEntity, Long> {

    boolean existsByCollaboration_IdAndUser_Id(Long collaborationId, String userId);

    Optional<CollaborationParticipantEntity> findByCollaboration_IdAndUser_Id(Long collaborationId, String userId);

    List<CollaborationParticipantEntity> findByCollaboration_Id(Long collaborationId);

    void deleteByCollaboration_IdAndUser_Id(Long collaborationId, String userId);

    /**
     * Returns all collaboration participations for a user with the given role.
     * Used by AccessRequestServiceImpl to find the set of collaborations where
     * the current user is a MANAGER, so their root folder IDs can be resolved.
     */
    List<CollaborationParticipantEntity> findByUser_IdAndCollaborationRole(
            String userId, CollaborationRole collaborationRole);
}
