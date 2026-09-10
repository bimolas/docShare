package com.subject77.docshare.modules.dm.repository;

import com.subject77.docshare.modules.dm.entities.DirectMessageReadStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DirectMessageReadStateRepository extends JpaRepository<DirectMessageReadStateEntity, Long> {

    Optional<DirectMessageReadStateEntity> findByThread_IdAndUser_Id(Long threadId, String userId);
}
