package com.subject77.docshare.modules.organization.repository;

import com.subject77.docshare.modules.organization.entities.OrganizationInvitationEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationInvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitationEntity, Long> {

    Optional<OrganizationInvitationEntity> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM OrganizationInvitationEntity i WHERE i.token = :token")
    Optional<OrganizationInvitationEntity> findByTokenForUpdate(@Param("token") String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM OrganizationInvitationEntity i WHERE i.id = :id")
    Optional<OrganizationInvitationEntity> findByIdForUpdate(@Param("id") Long id);

    List<OrganizationInvitationEntity> findByOrganization_OrgCodeAndStatus(String orgCode, OrganizationInvitationStatus status);

    List<OrganizationInvitationEntity> findByInviteeEmailAndStatus(String inviteeEmail, OrganizationInvitationStatus status);

    List<OrganizationInvitationEntity> findByStatusAndExpiresAtBefore(OrganizationInvitationStatus status, Instant now);

    boolean existsByOrganization_OrgCodeAndInviteeEmailAndStatus(String orgCode, String inviteeEmail, OrganizationInvitationStatus status);

    long countByOrganization_OrgCodeAndStatus(String orgCode, OrganizationInvitationStatus status);
}


