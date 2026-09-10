package com.subject77.docshare.modules.permission.repository;

import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<PermissionEntity, Long> {
    List<PermissionEntity> findByTargetUserEmailOrTargetOrgCode(String targetUserEmail, String targetOrgCode);

    List<PermissionEntity> findByTargetUserEmail(String targetUserEmail);

    List<PermissionEntity> findByTargetOrgCode(String targetOrgCode);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM PermissionEntity p WHERE " +
            "p.targetUserEmail IS NOT NULL AND LOWER(p.targetUserEmail) = LOWER(:targetUserEmail) AND (" +
            "(p.targetOrgCode IS NOT NULL AND UPPER(p.targetOrgCode) = UPPER(:targetOrgCode)) OR " +
            "(p.folder IS NOT NULL AND p.folder.ownerOrg IS NOT NULL AND UPPER(p.folder.ownerOrg.orgCode) = UPPER(:targetOrgCode)) OR " +
            "(p.document IS NOT NULL AND p.document.ownerOrg IS NOT NULL AND UPPER(p.document.ownerOrg.orgCode) = UPPER(:targetOrgCode))" +
            ")")
    List<PermissionEntity> findPermissionsForUserInOrg(
            @org.springframework.data.repository.query.Param("targetUserEmail") String targetUserEmail,
            @org.springframework.data.repository.query.Param("targetOrgCode") String targetOrgCode);

    List<PermissionEntity> findByTargetGroup_Id(Long groupId);

    List<PermissionEntity> findByTargetGroup_IdAndRevokedFalse(Long groupId);

    @org.springframework.data.jpa.repository.Query("SELECT p FROM PermissionEntity p WHERE " +
            "(p.targetUserEmail IS NOT NULL AND LOWER(p.targetUserEmail) = LOWER(:targetUserEmail)) OR " +
            "(:targetOrgCode IS NOT NULL AND p.targetOrgCode IS NOT NULL AND UPPER(p.targetOrgCode) = UPPER(:targetOrgCode)) OR " +
            "(p.targetGroup IS NOT NULL AND p.targetGroup.id IN :groupIds)")
    List<PermissionEntity> findPermissionsForUserOrOrgOrGroups(
            @org.springframework.data.repository.query.Param("targetUserEmail") String targetUserEmail,
            @org.springframework.data.repository.query.Param("targetOrgCode") String targetOrgCode,
            @org.springframework.data.repository.query.Param("groupIds") List<Long> groupIds);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT p FROM PermissionEntity p WHERE " +
            "(p.targetUserEmail IS NOT NULL AND LOWER(p.targetUserEmail) = LOWER(:targetUserEmail)) OR " +
            "(p.targetOrgCode IS NOT NULL AND (p.targetOrgCode IN :targetOrgCodes OR UPPER(p.targetOrgCode) IN :targetOrgCodes OR LOWER(p.targetOrgCode) IN :targetOrgCodes)) OR " +
            "(p.targetGroup IS NOT NULL AND p.targetGroup.id IN :groupIds)")
    List<PermissionEntity> findPermissionsForUserOrOrgsOrGroups(
            @org.springframework.data.repository.query.Param("targetUserEmail") String targetUserEmail,
            @org.springframework.data.repository.query.Param("targetOrgCodes") java.util.Collection<String> targetOrgCodes,
            @org.springframework.data.repository.query.Param("groupIds") java.util.Collection<Long> groupIds);

    List<PermissionEntity> findByTargetUserEmailOrTargetOrgCodeIn(String targetUserEmail, java.util.Collection<String> targetOrgCodes);

    List<PermissionEntity> findByDocumentFileHash(String documentFileHash);

    List<PermissionEntity> findByDocument_Id(Long documentId);

    List<PermissionEntity> findByFolder_Id(Long folderId);

    List<PermissionEntity> findByDocument_IdAndRevokedFalse(Long documentId);

    List<PermissionEntity> findByFolder_IdAndRevokedFalse(Long folderId);

    Optional<PermissionEntity> findByShareLinkToken(String token);

    Optional<PermissionEntity> findByShareLinkTokenAndRevokedFalse(String token);

    List<PermissionEntity> findByShareLinkTokenAndPermissionTypeAndRevokedFalse(String shareLinkToken, com.subject77.docshare.modules.permission.entities.PermissionType permissionType);

    List<PermissionEntity> findByRevokedFalse();

    List<PermissionEntity> findByCollaboration_Id(Long collaborationId);

    List<PermissionEntity> findByCollaboration_IdAndRevokedFalse(Long collaborationId);

    List<PermissionEntity> findByCollaboration_IdAndTargetUserEmailIgnoreCaseAndRevokedFalse(Long collaborationId, String targetUserEmail);

    List<PermissionEntity> findByCollaboration_IdAndTargetGroup_IdAndRevokedFalse(Long collaborationId, Long targetGroupId);

    List<PermissionEntity> findByTargetGroup_IdAndFolder_IdAndRevokedFalse(Long groupId, Long folderId);

    List<PermissionEntity> findByTargetGroup_IdAndDocument_IdAndRevokedFalse(Long groupId, Long documentId);

    List<PermissionEntity> findByTargetUserEmailIgnoreCaseAndFolder_IdAndRevokedFalse(String targetUserEmail, Long folderId);

    List<PermissionEntity> findByTargetUserEmailIgnoreCaseAndDocument_IdAndRevokedFalse(String targetUserEmail, Long documentId);

    List<PermissionEntity> findByTargetUserEmailIgnoreCaseAndDocumentFileHashAndRevokedFalse(String targetUserEmail, String documentFileHash);

    List<PermissionEntity> findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
            RecipientType recipientType, String targetOrgCode, Long folderId);

    List<PermissionEntity> findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndDocument_IdAndRevokedFalse(
            RecipientType recipientType, String targetOrgCode, Long documentId);

    List<PermissionEntity> findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndDocumentFileHashAndRevokedFalse(
            RecipientType recipientType, String targetOrgCode, String documentFileHash);

    @org.springframework.data.jpa.repository.Query("""
            SELECT p FROM PermissionEntity p
            LEFT JOIN p.document d
            WHERE p.grantedBy.id = :grantedByUserId
              AND (d IS NULL OR d.status = com.subject77.docshare.modules.document.entities.DocumentStatus.ACTIVE)
            ORDER BY p.createdAt DESC
            """)
    List<PermissionEntity> findByGrantedBy_IdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("grantedByUserId") String grantedByUserId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {
            "grantedBy", "folder", "folder.ownerOrg", "document", "document.ownerOrg", "document.folder", "document.folder.ownerOrg"
    })
    @org.springframework.data.jpa.repository.Query("""
            SELECT p FROM PermissionEntity p
            LEFT JOIN p.document d
            WHERE LOWER(p.targetUserEmail) = LOWER(:targetUserEmail)
              AND (d IS NULL OR d.status = com.subject77.docshare.modules.document.entities.DocumentStatus.ACTIVE)
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<PermissionEntity> findByTargetUserEmailOrderByCreatedAtDescIdDesc(@org.springframework.data.repository.query.Param("targetUserEmail") String targetUserEmail);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {
            "folder", "folder.ownerOrg", "document", "document.ownerOrg", "document.folder", "document.folder.ownerOrg",
            "targetGroup", "targetGroup.organization", "collaboration"
    })
    @org.springframework.data.jpa.repository.Query("""
            SELECT p FROM PermissionEntity p
            LEFT JOIN p.document d
            LEFT JOIN p.folder f
            WHERE p.grantedBy.id = :grantedByUserId
              AND (d IS NULL OR d.status = com.subject77.docshare.modules.document.entities.DocumentStatus.ACTIVE)
              AND (p.shareLinkToken IS NULL OR p.shareLinkToken = '')
              AND (f IS NULL OR (
                  NOT EXISTS (
                      SELECT w.id FROM com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity w
                      WHERE w.rootFolder.id = f.id
                  )
                  AND NOT EXISTS (
                      SELECT c.id FROM com.subject77.docshare.modules.collaboration.entities.CollaborationEntity c
                      WHERE c.rootFolder.id = f.id
                  )
              ))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<PermissionEntity> findByGrantedBy_IdOrderByCreatedAtDescIdDesc(@org.springframework.data.repository.query.Param("grantedByUserId") String grantedByUserId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {
            "grantedBy", "folder", "folder.ownerOrg", "document", "document.ownerOrg",
            "document.folder", "document.folder.ownerOrg", "targetGroup", "targetGroup.organization",
            "collaboration", "collaboration.ownerOrganization", "collaboration.partnerOrganization"
    })
    @org.springframework.data.jpa.repository.Query("""
            SELECT p FROM PermissionEntity p
            LEFT JOIN p.document d
            LEFT JOIN p.folder f
            LEFT JOIN p.collaboration c
            LEFT JOIN p.targetGroup tg
            WHERE (
                (LOWER(p.targetUserEmail) = LOWER(:userEmail) AND (p.recipientType IN (com.subject77.docshare.modules.permission.entities.RecipientType.USER_PERSONAL, com.subject77.docshare.modules.permission.entities.RecipientType.USER_ORGANIZATION_MEMBER) OR p.recipientType IS NULL))
                OR (:hasTeams = true AND tg.id IN :teamIds AND (p.recipientType = com.subject77.docshare.modules.permission.entities.RecipientType.GROUP OR p.recipientType IS NULL))
                OR (:hasOrgs = true AND UPPER(p.targetOrgCode) IN :orgCodes AND p.recipientType = com.subject77.docshare.modules.permission.entities.RecipientType.ORGANIZATION AND (p.targetUserEmail IS NULL OR p.targetUserEmail = ''))
            )
              AND p.revoked = false
              AND p.suspended = false
              AND p.permissionType = com.subject77.docshare.modules.permission.entities.PermissionType.ALLOW
              AND (p.expiresAt IS NULL OR p.expiresAt > :now)
              AND (p.shareLinkToken IS NULL OR p.shareLinkToken = '')
              AND (p.grantedBy.id <> :userId)
              AND (d IS NULL OR d.status = com.subject77.docshare.modules.document.entities.DocumentStatus.ACTIVE)
              AND (c IS NULL OR (
                  c.status = com.subject77.docshare.modules.collaboration.entities.CollaborationStatus.ACTIVE
                  AND (c.endsAt IS NULL OR c.endsAt > :now)
              ))
              AND (f IS NULL OR (
                  NOT EXISTS (
                      SELECT w.id FROM com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity w
                      WHERE w.rootFolder.id = f.id
                  )
                  AND NOT EXISTS (
                      SELECT col.id FROM com.subject77.docshare.modules.collaboration.entities.CollaborationEntity col
                      WHERE col.rootFolder.id = f.id
                  )
              ))
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    List<PermissionEntity> findSharedWithMeCandidates(
            @org.springframework.data.repository.query.Param("userEmail") String userEmail,
            @org.springframework.data.repository.query.Param("userId") String userId,
            @org.springframework.data.repository.query.Param("teamIds") java.util.Collection<Long> teamIds,
            @org.springframework.data.repository.query.Param("hasTeams") boolean hasTeams,
            @org.springframework.data.repository.query.Param("orgCodes") java.util.Collection<String> orgCodes,
            @org.springframework.data.repository.query.Param("hasOrgs") boolean hasOrgs,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(p) FROM PermissionEntity p
            WHERE p.revoked = false
              AND (p.expiresAt IS NULL OR p.expiresAt > :now)
              AND p.targetGroup IS NULL
              AND p.collaboration IS NULL
              AND p.workspaceId IS NULL
              AND (
                  p.grantedBy.id = :userId
                  OR (p.targetUserEmail IS NOT NULL AND LOWER(p.targetUserEmail) = LOWER(:userEmail))
              )
            """)
    long countActiveDirectSharesForUser(
            @org.springframework.data.repository.query.Param("userId") String userId,
            @org.springframework.data.repository.query.Param("userEmail") String userEmail,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);
}


