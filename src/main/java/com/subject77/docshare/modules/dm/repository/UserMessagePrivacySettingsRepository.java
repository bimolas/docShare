package com.subject77.docshare.modules.dm.repository;

import com.subject77.docshare.modules.dm.entities.UserMessagePrivacySettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserMessagePrivacySettingsRepository extends JpaRepository<UserMessagePrivacySettingsEntity, Long> {

    Optional<UserMessagePrivacySettingsEntity> findByUser_Id(String userId);
}
