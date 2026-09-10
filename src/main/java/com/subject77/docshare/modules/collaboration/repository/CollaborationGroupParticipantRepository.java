package com.subject77.docshare.modules.collaboration.repository;

import com.subject77.docshare.modules.collaboration.entities.CollaborationGroupParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollaborationGroupParticipantRepository extends JpaRepository<CollaborationGroupParticipantEntity, Long> {
    boolean existsByCollaboration_IdAndGroup_Id(Long collaborationId, Long groupId);
    Optional<CollaborationGroupParticipantEntity> findByCollaboration_IdAndGroup_Id(Long collaborationId, Long groupId);
    List<CollaborationGroupParticipantEntity> findByCollaboration_Id(Long collaborationId);
}
