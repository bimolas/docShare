package com.subject77.docshare.modules.document.repository;

import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findByFolder_Id(Long folderId);

    List<DocumentEntity> findByFolderIsNullAndOwnerOrg_OrgCode(String orgCode);

    List<DocumentEntity> findByFileHash(String fileHash);

    List<DocumentEntity> findByOwnerOrg_OrgCode(String orgCode);

    long countByUploadedBy_Id(String userId);

    long countByOwnerOrg_OrgCode(String orgCode);

    /**
     * Idempotency check: find an existing PENDING_UPLOAD record for the same
     * uploader, file hash, status, and folder (which may be null for root uploads).
     * Spring Data cannot express null-equality via method name derivation, so we
     * use a JPQL query with IS NULL / = :folderId branching.
     */
    @Query("""
            SELECT d FROM DocumentEntity d
            WHERE d.fileHash = :fileHash
              AND d.uploadedBy.id = :userId
              AND d.status = :status
              AND ((:folderId IS NULL AND d.folder IS NULL) OR d.folder.id = :folderId)
            """)
    Optional<DocumentEntity> findByFileHashAndUploadedBy_IdAndStatusAndFolder_Id(
            @Param("fileHash") String fileHash,
            @Param("userId") String userId,
            @Param("status") DocumentStatus status,
            @Param("folderId") Long folderId);

    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM DocumentEntity d WHERE d.uploadedBy.id = :userId")
    Long sumFileSizeByUserId(@Param("userId") String userId);

    @Query("SELECT COALESCE(SUM(d.fileSize), 0) FROM DocumentEntity d WHERE d.ownerOrg.orgCode = :orgCode")
    Long sumFileSizeByOrgCode(@Param("orgCode") String orgCode);
}
