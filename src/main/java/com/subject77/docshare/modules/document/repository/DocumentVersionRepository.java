package com.subject77.docshare.modules.document.repository;

import com.subject77.docshare.modules.document.entities.DocumentVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersionEntity, Long> {
    List<DocumentVersionEntity> findByDocument_IdOrderByVersionNumberDesc(Long documentId);

    Optional<DocumentVersionEntity> findByDocument_IdAndVersionNumber(Long documentId, Integer versionNumber);
}
