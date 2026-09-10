package com.subject77.docshare.modules.dm.repository;

import com.subject77.docshare.modules.dm.entities.UserBlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlockEntity, Long> {

    boolean existsByBlockingUser_IdAndBlockedUser_Id(String blockingUserId, String blockedUserId);

    Optional<UserBlockEntity> findByBlockingUser_IdAndBlockedUser_Id(String blockingUserId, String blockedUserId);

    List<UserBlockEntity> findByBlockingUser_Id(String blockingUserId);

    void deleteByBlockingUser_IdAndBlockedUser_Id(String blockingUserId, String blockedUserId);
}
