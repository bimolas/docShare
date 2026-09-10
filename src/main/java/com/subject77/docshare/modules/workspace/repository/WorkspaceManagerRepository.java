package com.subject77.docshare.modules.workspace.repository;

import com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceManagerRepository extends JpaRepository<WorkspaceManagerEntity, Long> {

    boolean existsByWorkspace_IdAndUser_Id(Long workspaceId, String userId);

    Optional<WorkspaceManagerEntity> findByWorkspace_IdAndUser_Id(Long workspaceId, String userId);

    List<WorkspaceManagerEntity> findByWorkspace_Id(Long workspaceId);

    /** All workspace-manager assignments for a given user. */
    List<WorkspaceManagerEntity> findByUser_Id(String userId);

    void deleteByWorkspace_IdAndUser_Id(Long workspaceId, String userId);

    /** All workspace-manager assignments for a user within a specific organization. */
    List<WorkspaceManagerEntity> findByUser_IdAndWorkspace_Organization_OrgCode(String userId, String orgCode);
}
