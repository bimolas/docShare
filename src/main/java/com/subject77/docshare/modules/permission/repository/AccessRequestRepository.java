package com.subject77.docshare.modules.permission.repository;

import com.subject77.docshare.modules.permission.entities.AccessRequestEntity;
import com.subject77.docshare.modules.permission.entities.AccessRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequestEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ar FROM AccessRequestEntity ar WHERE ar.id = :id")
    Optional<AccessRequestEntity> findByIdForUpdate(@Param("id") Long id);

    Page<AccessRequestEntity> findByRequester_Id(String requesterId, Pageable pageable);

    Page<AccessRequestEntity> findByRequester_IdAndStatus(String requesterId, AccessRequestStatus status, Pageable pageable);

    boolean existsByRequester_IdAndDocument_IdAndStatus(String requesterId, Long documentId, AccessRequestStatus status);

    boolean existsByRequester_IdAndFolder_IdAndStatus(String requesterId, Long folderId, AccessRequestStatus status);

    java.util.Optional<AccessRequestEntity> findFirstByRequester_IdAndDocument_IdAndStatus(String requesterId, Long documentId, AccessRequestStatus status);

    java.util.Optional<AccessRequestEntity> findFirstByRequester_IdAndFolder_IdAndStatus(String requesterId, Long folderId, AccessRequestStatus status);

    @Query("SELECT ar FROM AccessRequestEntity ar WHERE ar.requester.id = :requesterId " +
           "AND (:status IS NULL OR ar.status = :status) " +
           "AND (:search IS NULL OR LOWER(ar.document.fileName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(ar.folder.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(ar.reason) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<AccessRequestEntity> findMyAccessRequests(
            @Param("requesterId") String requesterId,
            @Param("status") AccessRequestStatus status,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT ar FROM AccessRequestEntity ar WHERE ar.status = :status AND (" +
           "ar.document.uploadedBy.id = :userId OR " +
           "ar.folder.createdBy.id = :userId OR " +
           "(ar.document IS NOT NULL AND ar.document.ownerOrg IS NOT NULL AND ar.document.ownerOrg.orgCode IN :adminOrgCodes) OR " +
           "(ar.folder IS NOT NULL AND ar.folder.ownerOrg IS NOT NULL AND ar.folder.ownerOrg.orgCode IN :adminOrgCodes))")
    Page<AccessRequestEntity> findPendingReviewsForUser(
            @Param("userId") String userId,
            @Param("status") AccessRequestStatus status,
            @Param("adminOrgCodes") Collection<String> adminOrgCodes,
            Pageable pageable);

    @Query("SELECT ar FROM AccessRequestEntity ar WHERE ar.status = :status AND (" +
           "ar.document.uploadedBy.id = :userId OR " +
           "ar.folder.createdBy.id = :userId)")
    Page<AccessRequestEntity> findPendingReviewsForUserDirect(
            @Param("userId") String userId,
            @Param("status") AccessRequestStatus status,
            Pageable pageable);

    @Query("SELECT ar FROM AccessRequestEntity ar WHERE (:status IS NULL OR ar.status = :status) AND (" +
           "ar.document.uploadedBy.id = :userId OR " +
           "ar.folder.createdBy.id = :userId OR " +
           "(ar.document IS NOT NULL AND ar.document.ownerOrg IS NOT NULL AND ar.document.ownerOrg.orgCode IN :adminOrgCodes) OR " +
           "(ar.folder IS NOT NULL AND ar.folder.ownerOrg IS NOT NULL AND ar.folder.ownerOrg.orgCode IN :adminOrgCodes))")
    Page<AccessRequestEntity> findReviewsForUser(
            @Param("userId") String userId,
            @Param("status") AccessRequestStatus status,
            @Param("adminOrgCodes") Collection<String> adminOrgCodes,
            Pageable pageable);

    @Query("SELECT ar FROM AccessRequestEntity ar WHERE (:status IS NULL OR ar.status = :status) AND (" +
           "ar.document.uploadedBy.id = :userId OR " +
           "ar.folder.createdBy.id = :userId)")
    Page<AccessRequestEntity> findReviewsForUserDirect(
            @Param("userId") String userId,
            @Param("status") AccessRequestStatus status,
            Pageable pageable);

    @Query("SELECT ar FROM AccessRequestEntity ar WHERE ar.status = :status AND (" +
           "(ar.folder IS NOT NULL AND ar.folder.id IN :rootFolderIds) OR " +
           "(ar.document IS NOT NULL AND ar.document.folder IS NOT NULL AND ar.document.folder.id IN :rootFolderIds))")
    Page<AccessRequestEntity> findPendingReviewsForWorkspaceManager(
            @Param("status") AccessRequestStatus status,
            @Param("rootFolderIds") Collection<Long> rootFolderIds,
            Pageable pageable);

    @Query("SELECT ar FROM AccessRequestEntity ar WHERE (:status IS NULL OR ar.status = :status) AND (" +
           "(ar.folder IS NOT NULL AND ar.folder.id IN :rootFolderIds) OR " +
           "(ar.document IS NOT NULL AND ar.document.folder IS NOT NULL AND ar.document.folder.id IN :rootFolderIds))")
    Page<AccessRequestEntity> findReviewsForRootFolderIds(
            @Param("status") AccessRequestStatus status,
            @Param("rootFolderIds") Collection<Long> rootFolderIds,
            Pageable pageable);

    @Query("SELECT ar FROM AccessRequestEntity ar WHERE ar.status = :status AND (" +
           "(ar.folder IS NOT NULL AND ar.folder.id IN :rootFolderIds) OR " +
           "(ar.document IS NOT NULL AND ar.document.folder IS NOT NULL AND ar.document.folder.id IN :rootFolderIds))")
    Page<AccessRequestEntity> findPendingReviewsForCollaborationManager(
            @Param("status") AccessRequestStatus status,
            @Param("rootFolderIds") Collection<Long> rootFolderIds,
            Pageable pageable);

    @Query("SELECT ar FROM AccessRequestEntity ar WHERE ar.status = :status AND (" +
           "(ar.document IS NOT NULL AND ar.document.ownerOrg IS NOT NULL AND ar.document.ownerOrg.orgCode = :orgCode) OR " +
           "(ar.folder IS NOT NULL AND ar.folder.ownerOrg IS NOT NULL AND ar.folder.ownerOrg.orgCode = :orgCode))")
    java.util.List<AccessRequestEntity> findPendingByOrgCode(
            @Param("status") AccessRequestStatus status,
            @Param("orgCode") String orgCode);

    @Query("SELECT ar FROM AccessRequestEntity ar WHERE ar.status = :status AND (" +
           "(ar.document IS NOT NULL AND ar.document.ownerOrg IS NULL AND ar.document.uploadedBy.id = :userId) OR " +
           "(ar.folder IS NOT NULL AND ar.folder.ownerOrg IS NULL AND ar.folder.createdBy.id = :userId))")
    java.util.List<AccessRequestEntity> findPendingByPersonalUserId(
            @Param("status") AccessRequestStatus status,
            @Param("userId") String userId);

    long countByRequester_IdAndStatus(String requesterId, AccessRequestStatus status);

    @Query("SELECT COUNT(ar) FROM AccessRequestEntity ar WHERE ar.status = :status AND (" +
           "(ar.document IS NOT NULL AND ar.document.ownerOrg IS NOT NULL AND ar.document.ownerOrg.orgCode = :orgCode) OR " +
           "(ar.folder IS NOT NULL AND ar.folder.ownerOrg IS NOT NULL AND ar.folder.ownerOrg.orgCode = :orgCode))")
    long countPendingByOrgCode(
            @Param("status") AccessRequestStatus status,
            @Param("orgCode") String orgCode);

    @Query("SELECT COUNT(ar) FROM AccessRequestEntity ar WHERE ar.status = :status AND (" +
           "(ar.document IS NOT NULL AND ar.document.ownerOrg IS NULL AND ar.document.uploadedBy.id = :userId) OR " +
           "(ar.folder IS NOT NULL AND ar.folder.ownerOrg IS NULL AND ar.folder.createdBy.id = :userId))")
    long countPendingByPersonalUserId(
            @Param("status") AccessRequestStatus status,
            @Param("userId") String userId);
}


