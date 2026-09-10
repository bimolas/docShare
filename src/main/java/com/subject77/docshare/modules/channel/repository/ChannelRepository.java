package com.subject77.docshare.modules.channel.repository;

import com.subject77.docshare.modules.channel.entities.ChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChannelRepository extends JpaRepository<ChannelEntity, Long> {

    List<ChannelEntity> findByOrganization_OrgCode(String orgCode);

    List<ChannelEntity> findByTeam_Id(Long teamId);

    List<ChannelEntity> findByProjectWorkspace_Id(Long projectWorkspaceId);

    List<ChannelEntity> findByCollaboration_Id(Long collaborationId);

    boolean existsByOrganization_OrgCodeAndName(String orgCode, String name);

    boolean existsByTeam_IdAndName(Long teamId, String name);

    boolean existsByProjectWorkspace_IdAndName(Long projectWorkspaceId, String name);

    boolean existsByCollaboration_IdAndName(Long collaborationId, String name);
}
