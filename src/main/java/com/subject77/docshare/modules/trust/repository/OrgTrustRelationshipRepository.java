package com.subject77.docshare.modules.trust.repository;

import com.subject77.docshare.modules.trust.entities.OrgTrustRelationshipEntity;
import com.subject77.docshare.modules.trust.entities.TrustStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrgTrustRelationshipRepository extends JpaRepository<OrgTrustRelationshipEntity, Long> {

    @Query("SELECT t FROM OrgTrustRelationshipEntity t WHERE " +
           "(t.sourceOrganization.orgCode = :orgCode OR t.targetOrganization.orgCode = :orgCode)")
    List<OrgTrustRelationshipEntity> findByOrganizationCode(@Param("orgCode") String orgCode);

    @Query("SELECT t FROM OrgTrustRelationshipEntity t WHERE " +
           "((t.sourceOrganization.orgCode = :org1 AND t.targetOrganization.orgCode = :org2) OR " +
           " (t.sourceOrganization.orgCode = :org2 AND t.targetOrganization.orgCode = :org1)) AND " +
           "t.status IN (:statuses)")
    List<OrgTrustRelationshipEntity> findByOrganizationPairAndStatusIn(
            @Param("org1") String org1,
            @Param("org2") String org2,
            @Param("statuses") List<TrustStatus> statuses);

    @Query("SELECT t FROM OrgTrustRelationshipEntity t WHERE " +
           "((t.sourceOrganization.orgCode = :org1 AND t.targetOrganization.orgCode = :org2) OR " +
           " (t.sourceOrganization.orgCode = :org2 AND t.targetOrganization.orgCode = :org1)) AND " +
           "t.status = com.subject77.docshare.modules.trust.entities.TrustStatus.ACTIVE AND " +
           "(t.expiresAt IS NULL OR t.expiresAt > :now)")
    List<OrgTrustRelationshipEntity> findActiveTrustBetween(
            @Param("org1") String org1,
            @Param("org2") String org2,
            @Param("now") Instant now);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM OrgTrustRelationshipEntity t WHERE t.id = :id")
    java.util.Optional<OrgTrustRelationshipEntity> findByIdForUpdate(@Param("id") Long id);
}
