package com.subject77.docshare.modules.organization.repository;

import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserGroupRepository extends JpaRepository<UserGroupEntity, Long> {

    List<UserGroupEntity> findByGroup_Id(Long groupId);

    List<UserGroupEntity> findByGroup_IdAndRole(Long groupId, GroupMemberRole role);

    List<UserGroupEntity> findByUser_Id(String userId);

    List<UserGroupEntity> findByUser_IdAndGroup_Organization_OrgCode(String userId, String orgCode);

    long countByGroup_IdAndGroup_Organization_OrgCode(Long groupId, String orgCode);

    boolean existsByUser_IdAndGroup_Id(String userId, Long groupId);

    boolean existsByUser_IdAndGroup_IdAndRole(String userId, Long groupId, GroupMemberRole role);

    Optional<UserGroupEntity> findByUser_IdAndGroup_Id(String userId, Long groupId);

    void deleteByUser_IdAndGroup_Id(String userId, Long groupId);

    /**
     * Returns all members of {@code groupId} whose organisation membership in
     * the group's owning organisation is currently {@link MembershipStatus#ACTIVE}.
     *
     * <p>Joins through {@code UserOrganizationEntity} (the authoritative org-membership
     * table) rather than through {@code UserEntity.organization} (which stores the
     * user's primary org but is not the membership status record).</p>
     *
     * <p>Used by the File Request service to fan-out notifications and to
     * validate upload attempts for TEAM-recipient requests.</p>
     */
    @Query("""
            SELECT ug FROM UserGroupEntity ug
            JOIN ug.group g
            WHERE ug.group.id = :groupId
              AND EXISTS (
                  SELECT uo FROM UserOrganizationEntity uo
                  WHERE uo.user.id = ug.user.id
                    AND uo.organization.orgCode = g.organization.orgCode
                    AND uo.status = :orgMembershipStatus
              )
            """)
    List<UserGroupEntity> findActiveOrgMembersByGroupId(
            @Param("groupId") Long groupId,
            @Param("orgMembershipStatus") MembershipStatus orgMembershipStatus);

    /**
     * Checks whether {@code userId} is a member of {@code groupId} AND holds
     * an {@link MembershipStatus#ACTIVE} membership in the group's organisation.
     *
     * <p>This is the authoritative upload-eligibility check for TEAM file
     * requests.  The membership is evaluated at the moment of the upload
     * (dynamic, not snapshotted at request creation time).</p>
     */
    @Query("""
            SELECT COUNT(ug) > 0 FROM UserGroupEntity ug
            JOIN ug.group g
            WHERE ug.group.id = :groupId
              AND ug.user.id = :userId
              AND EXISTS (
                  SELECT uo FROM UserOrganizationEntity uo
                  WHERE uo.user.id = :userId
                    AND uo.organization.orgCode = g.organization.orgCode
                    AND uo.status = :orgMembershipStatus
              )
            """)
    boolean isActiveOrgMemberOfGroup(
            @Param("userId") String userId,
            @Param("groupId") Long groupId,
            @Param("orgMembershipStatus") MembershipStatus orgMembershipStatus);
}
