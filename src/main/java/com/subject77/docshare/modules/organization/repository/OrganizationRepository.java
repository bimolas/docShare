package com.subject77.docshare.modules.organization.repository;

import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, String> {
    boolean existsByOrgCode(String orgCode);

    Optional<OrganizationEntity> findByOrgCode(String orgCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrganizationEntity o WHERE o.orgCode = :orgCode")
    Optional<OrganizationEntity> findByOrgCodeForUpdate(@Param("orgCode") String orgCode);

    List<OrganizationEntity> findByCreatedBy_Id(String userId);

    List<OrganizationEntity> findByNameContainingIgnoreCaseOrOrgCodeContainingIgnoreCase(String name, String orgCode, Pageable pageable);
}
