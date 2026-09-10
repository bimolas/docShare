package com.subject77.docshare.modules.channel.repository;

import com.subject77.docshare.modules.channel.entities.ChannelReadStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChannelReadStateRepository extends JpaRepository<ChannelReadStateEntity, Long> {

    Optional<ChannelReadStateEntity> findByChannel_IdAndUser_Id(Long channelId, String userId);
}
