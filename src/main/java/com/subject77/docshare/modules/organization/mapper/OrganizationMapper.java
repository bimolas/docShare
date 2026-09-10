package com.subject77.docshare.modules.organization.mapper;

import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.dto.OrganizationRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationResponseDto;
import com.subject77.docshare.modules.user.entities.UserEntity;

public class OrganizationMapper {

    public static OrganizationResponseDto toResponseDto(OrganizationEntity entity) {
        if (entity == null)
            return null;

        return OrganizationResponseDto.builder()
                .orgCode(entity.getOrgCode())
                .name(entity.getName())
                .createdByUserId(entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public static OrganizationEntity toEntity(OrganizationRequestDto dto, UserEntity createdBy) {
        if (dto == null)
            return null;

        return OrganizationEntity.builder()
                .orgCode(dto.getOrgCode())
                .name(dto.getName())
                .createdBy(createdBy)
                // createdAt handled by @PrePersist
                .build();
    }
}
