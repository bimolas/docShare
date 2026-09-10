package com.subject77.docshare.modules.audit.services;

import com.subject77.docshare.modules.audit.dto.UnifiedAuditEventResponseDto;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditQueryCriteria;
import com.subject77.docshare.modules.user.entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UnifiedAuditService {

    Page<UnifiedAuditEventResponseDto> queryEvents(
            UnifiedAuditQueryCriteria criteria,
            Pageable pageable,
            UserEntity currentUser);
}
