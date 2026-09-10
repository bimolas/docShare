package com.subject77.docshare.modules.collaboration.repository;

import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface CollaborationRepository extends JpaRepository<CollaborationEntity, Long> {

    List<CollaborationEntity> findByOwnerOrganization_OrgCodeOrPartnerOrganization_OrgCode(String ownerCode, String partnerCode);

    List<CollaborationEntity> findByOwnerOrganization_OrgCode(String ownerOrgCode);

    List<CollaborationEntity> findByPartnerOrganization_OrgCode(String partnerOrgCode);

    List<CollaborationEntity> findByStatus(CollaborationStatus status);

    List<CollaborationEntity> findByStatusAndEndsAtBefore(CollaborationStatus status, Instant cutoff);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT c FROM CollaborationEntity c WHERE c.id = :id")
    java.util.Optional<CollaborationEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
