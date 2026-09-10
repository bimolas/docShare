package com.subject77.docshare.modules.user.repository;

import com.subject77.docshare.modules.user.entities.UserEntity;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") String id);

    Optional<UserEntity> findByEmail(String email);

    List<UserEntity> findByOrganization_OrgCode(String orgCode);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM UserEntity u WHERE " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<UserEntity> searchUsers(@Param("query") String query, Pageable pageable);

    @Query("SELECT u FROM UserEntity u WHERE LOWER(u.email) IN :emails")
    List<UserEntity> findByEmailInIgnoreCase(@Param("emails") java.util.Collection<String> emails);
}
