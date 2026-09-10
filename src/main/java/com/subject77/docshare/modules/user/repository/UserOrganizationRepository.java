package com.subject77.docshare.modules.user.repository;

import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserOrganizationRepository extends JpaRepository<UserOrganizationEntity, Long> {
    List<UserOrganizationEntity> findByUser_Id(String userId);

    List<UserOrganizationEntity> findByUser_IdAndStatus(String userId, MembershipStatus status);

    List<UserOrganizationEntity> findByOrganization_OrgCode(String orgCode);

    List<UserOrganizationEntity> findByOrganization_OrgCodeAndStatus(String orgCode, MembershipStatus status);

    Page<UserOrganizationEntity> findByOrganization_OrgCodeAndStatus(String orgCode, MembershipStatus status, Pageable pageable);

    Optional<UserOrganizationEntity> findByUser_IdAndOrganization_OrgCode(String userId, String orgCode);

    boolean existsByUser_IdAndOrganization_OrgCode(String userId, String orgCode);

    boolean existsByUser_IdAndOrganization_OrgCodeAndStatus(String userId, String orgCode, MembershipStatus status);

    boolean existsByUser_EmailAndOrganization_OrgCode(String userEmail, String orgCode);

    boolean existsByUser_EmailAndOrganization_OrgCodeAndStatus(String userEmail, String orgCode, MembershipStatus status);

    List<UserOrganizationEntity> findByUser_IdAndOrganization_OrgCodeInAndStatus(String userId, Collection<String> orgCodes, MembershipStatus status);

    List<UserOrganizationEntity> findByUser_EmailAndOrganization_OrgCodeInAndStatus(String userEmail, Collection<String> orgCodes, MembershipStatus status);

    long countByOrganization_OrgCodeAndStatusAndRoleIn(String orgCode, MembershipStatus status, Collection<Role> roles);

    long countByOrganization_OrgCodeAndStatus(String orgCode, MembershipStatus status);
}

