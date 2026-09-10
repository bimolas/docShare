package com.subject77.docshare.modules.folder.repository;

import com.subject77.docshare.modules.folder.entities.FolderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<FolderEntity, Long> {
    List<FolderEntity> findByParentFolderIsNullAndOwnerOrg_OrgCode(String orgCode);

    List<FolderEntity> findByParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(String userId);

    List<FolderEntity> findByParentFolder_Id(Long parentFolderId);

    boolean existsByNameAndParentFolder_IdAndOwnerOrg_OrgCode(String name, Long parentFolderId, String orgCode);

    boolean existsByNameAndParentFolder_IdAndOwnerOrgIsNullAndCreatedBy_Id(String name, Long parentFolderId, String userId);

    boolean existsByNameAndParentFolderIsNullAndOwnerOrg_OrgCode(String name, String orgCode);

    Optional<FolderEntity> findByNameAndParentFolderIsNullAndOwnerOrg_OrgCode(String name, String orgCode);

    boolean existsByNameAndParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(String name, String userId);

    long countByCreatedBy_Id(String userId);

    long countByOwnerOrg_OrgCode(String orgCode);

    Optional<FolderEntity> findByIsDefaultSubspaceTrueAndParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(String userId);

    Optional<FolderEntity> findByIsDefaultSubspaceTrueAndParentFolderIsNullAndOwnerOrg_OrgCode(String orgCode);

    @org.springframework.data.jpa.repository.Query(value = """
        WITH RECURSIVE folder_tree (id) AS (
            SELECT id FROM folders WHERE id = :rootFolderId
            UNION ALL
            SELECT f.id FROM folders f JOIN folder_tree ft ON f.parent_folder_id = ft.id
        )
        SELECT id FROM folder_tree
    """, nativeQuery = true)
    List<Long> findSubtreeFolderIds(@org.springframework.data.repository.query.Param("rootFolderId") Long rootFolderId);
}
