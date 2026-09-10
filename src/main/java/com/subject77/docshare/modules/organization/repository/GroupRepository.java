package com.subject77.docshare.modules.organization.repository;

import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, Long> {
    List<GroupEntity> findByOrganization_OrgCode(String orgCode);
    List<GroupEntity> findByOrganization_OrgCodeAndStatus(String orgCode, GroupStatus status);
    Optional<GroupEntity> findByIdAndOrganization_OrgCode(Long id, String orgCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GroupEntity g WHERE g.id = :id AND g.organization.orgCode = :orgCode")
    Optional<GroupEntity> findByIdAndOrganization_OrgCodeForUpdate(@Param("id") Long id, @Param("orgCode") String orgCode);

    boolean existsByNameAndOrganization_OrgCode(String name, String orgCode);

    @Query("SELECT g.id, COUNT(ug.id) FROM GroupEntity g LEFT JOIN UserGroupEntity ug ON ug.group.id = g.id WHERE g.organization.orgCode = :orgCode GROUP BY g.id")
    List<Object[]> findGroupIdsWithMemberCountByOrgCode(@Param("orgCode") String orgCode);

    @Query("SELECT COUNT(ug.id) FROM UserGroupEntity ug WHERE ug.group.id = :groupId")
    long countMembersByGroupId(@Param("groupId") Long groupId);

    long countByOrganization_OrgCodeAndStatus(String orgCode, GroupStatus status);
}

