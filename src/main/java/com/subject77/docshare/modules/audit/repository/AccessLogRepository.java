package com.subject77.docshare.modules.audit.repository;

import com.subject77.docshare.modules.audit.entities.AccessLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface AccessLogRepository extends JpaRepository<AccessLogEntity, Long>, JpaSpecificationExecutor<AccessLogEntity> {
    List<AccessLogEntity> findByDocumentIdOrderByTimestampDesc(Long documentId);

    List<AccessLogEntity> findByDocumentFileHashOrderByTimestampDesc(String documentFileHash);

    @Query("SELECT a FROM AccessLogEntity a WHERE a.folder.id = :folderId OR (a.resourceType = 'FOLDER' AND a.resourceId = :folderId) ORDER BY a.timestamp DESC")
    List<AccessLogEntity> findByFolder_IdOrderByTimestampDesc(@Param("folderId") Long folderId);

    List<AccessLogEntity> findByOrgCodeOrderByTimestampDesc(String orgCode);

    List<AccessLogEntity> findByAccessorEmailOrderByTimestampDesc(String email);

    List<AccessLogEntity> findByAccessorEmailAndTimestampBetween(String email, java.time.Instant start, java.time.Instant end);

    List<AccessLogEntity> findByWorkspaceIdOrderByTimestampDesc(Long workspaceId);

    List<AccessLogEntity> findByOrgCodeAndTimestampBetween(String orgCode, java.time.Instant start, java.time.Instant end);
}
