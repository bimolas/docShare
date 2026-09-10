package com.subject77.docshare.modules.channel.repository;

import com.subject77.docshare.modules.channel.entities.ChannelMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelMemberRepository extends JpaRepository<ChannelMemberEntity, Long> {

    List<ChannelMemberEntity> findByChannel_Id(Long channelId);

    Optional<ChannelMemberEntity> findByChannel_IdAndUser_Id(Long channelId, String userId);

    boolean existsByChannel_IdAndUser_Id(Long channelId, String userId);

    void deleteByChannel_IdAndUser_Id(Long channelId, String userId);

    long countByChannel_Id(Long channelId);

    List<ChannelMemberEntity> findByUser_Id(String userId);
}
