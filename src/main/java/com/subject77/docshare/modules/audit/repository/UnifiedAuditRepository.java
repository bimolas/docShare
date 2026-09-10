package com.subject77.docshare.modules.audit.repository;

import com.subject77.docshare.modules.audit.dto.AuditProvenanceDto;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditEventResponseDto;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditQueryCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UnifiedAuditRepository {

    Page<UnifiedAuditEventResponseDto> queryUnifiedAuditEvents(
            UnifiedAuditQueryCriteria criteria,
            List<Long> folderDescendantIds,
            String currentUserId,
            boolean canViewSensitiveMetadata,
            Pageable pageable);

    Optional<AuditProvenanceDto> findDeletedResourceProvenance(String resourceType, Long resourceId);
}
