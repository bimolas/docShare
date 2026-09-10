package com.subject77.docshare.modules.audit.repository;

import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermissionAuditLogRepository extends JpaRepository<PermissionAuditLogEntity, Long>, JpaSpecificationExecutor<PermissionAuditLogEntity> {
    List<PermissionAuditLogEntity> findByTargetIdAndTargetTypeOrderByTimestampDesc(Long targetId, String targetType);

    List<PermissionAuditLogEntity> findByFolderIdOrderByTimestampDesc(Long folderId);

    List<PermissionAuditLogEntity> findByOrgCodeOrderByTimestampDesc(String orgCode);

    List<PermissionAuditLogEntity> findByGrantedBy_IdOrderByTimestampDesc(String userId);

    List<PermissionAuditLogEntity> findByGrantedToEmailOrderByTimestampDesc(String email);
    List<PermissionAuditLogEntity> findByWorkspaceIdOrderByTimestampDesc(Long workspaceId);
    List<PermissionAuditLogEntity> findByGroupIdOrderByTimestampDesc(Long groupId);
}
