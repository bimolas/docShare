package com.subject77.docshare.modules.dm.repository;

import com.subject77.docshare.modules.dm.entities.DirectMessageThreadEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DirectMessageThreadRepository extends JpaRepository<DirectMessageThreadEntity, Long> {

    @Query("SELECT t FROM DirectMessageThreadEntity t WHERE (t.user1.id = :userAId AND t.user2.id = :userBId) OR (t.user1.id = :userBId AND t.user2.id = :userAId)")
    Optional<DirectMessageThreadEntity> findThreadBetweenUsers(@Param("userAId") String userAId, @Param("userBId") String userBId);

    @Query("SELECT t FROM DirectMessageThreadEntity t WHERE t.user1.id = :userId OR t.user2.id = :userId ORDER BY t.lastMessageAt DESC")
    Page<DirectMessageThreadEntity> findThreadsByUserId(@Param("userId") String userId, Pageable pageable);
}
