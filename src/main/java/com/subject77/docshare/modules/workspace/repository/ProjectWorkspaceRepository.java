package com.subject77.docshare.modules.workspace.repository;

import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectWorkspaceRepository extends JpaRepository<ProjectWorkspaceEntity, Long> {

    boolean existsByOrganization_OrgCode(String orgCode);

    boolean existsByOrganization_OrgCodeAndName(String orgCode, String name);

    boolean existsByOrganization_OrgCodeAndNameAndIdNot(String orgCode, String name, Long id);

    boolean existsByRootFolder_Id(Long rootFolderId);

    Optional<ProjectWorkspaceEntity> findFirstByRootFolder_Id(Long rootFolderId);

    List<ProjectWorkspaceEntity> findByRootFolder_IdIn(java.util.Collection<Long> rootFolderIds);

    Optional<ProjectWorkspaceEntity> findByIdAndOrganization_OrgCode(Long id, String orgCode);

    List<ProjectWorkspaceEntity> findByOrganization_OrgCode(String orgCode);

    // Personal Workspace Queries
    List<ProjectWorkspaceEntity> findByCreatedBy_IdAndOrganizationIsNull(String userId);

    boolean existsByCreatedBy_IdAndOrganizationIsNull(String userId);

    boolean existsByCreatedBy_IdAndNameAndOrganizationIsNull(String userId, String name);

    boolean existsByCreatedBy_IdAndNameAndOrganizationIsNullAndIdNot(String userId, String name, Long id);

    Optional<ProjectWorkspaceEntity> findByIdAndCreatedBy_IdAndOrganizationIsNull(Long id, String userId);

    long countByOrganization_OrgCodeAndStatus(String orgCode, com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus status);
}

