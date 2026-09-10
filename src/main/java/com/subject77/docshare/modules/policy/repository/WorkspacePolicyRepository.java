package com.subject77.docshare.modules.policy.repository;

import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.entities.WorkspaceType;
import com.subject77.docshare.modules.user.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkspacePolicyRepository extends JpaRepository<WorkspacePolicyEntity, Long> {

    Optional<WorkspacePolicyEntity> findByOrganization_OrgCode(String orgCode);

    Optional<WorkspacePolicyEntity> findByOrganization(OrganizationEntity organization);

    boolean existsByOrganization_OrgCode(String orgCode);

    Optional<WorkspacePolicyEntity> findByUser_Id(String userId);

    Optional<WorkspacePolicyEntity> findByUser(UserEntity user);

    boolean existsByUser_Id(String userId);

    Optional<WorkspacePolicyEntity> findByWorkspaceTypeAndOrganization_OrgCode(WorkspaceType workspaceType, String orgCode);

    Optional<WorkspacePolicyEntity> findByWorkspaceTypeAndUser_Id(WorkspaceType workspaceType, String userId);
}

