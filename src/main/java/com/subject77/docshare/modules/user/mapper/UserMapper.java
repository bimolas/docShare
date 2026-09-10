package com.subject77.docshare.modules.user.mapper;

import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.user.dto.UserOrganizationMembershipDto;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.dto.UserRequestDto;
import com.subject77.docshare.modules.user.dto.UserResponseDto;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;

import java.util.List;
import java.util.stream.Collectors;

public class UserMapper {

    public static UserResponseDto toResponseDto(UserEntity entity) {
        return toResponseDto(entity, null);
    }

    public static UserResponseDto toResponseDto(UserEntity entity, List<UserOrganizationEntity> activeMemberships) {
        if (entity == null)
            return null;

        List<UserOrganizationMembershipDto> activeOrgs = null;
        if (activeMemberships != null) {
            activeOrgs = activeMemberships.stream()
                    .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                    .map(m -> UserOrganizationMembershipDto.builder()
                            .orgCode(m.getOrganization() != null ? m.getOrganization().getOrgCode() : null)
                            .orgName(m.getOrganization() != null ? m.getOrganization().getName() : null)
                            .role(m.getRole())
                            .status(m.getStatus())
                            .build())
                    .collect(Collectors.toList());
        }

        return UserResponseDto.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .orgCode(entity.getOrganization() != null ? entity.getOrganization().getOrgCode() : null)
                .role(entity.getRole())
                .createdAt(entity.getCreatedAt())
                .activeOrganizations(activeOrgs)
                .build();
    }

    public static UserEntity toEntity(UserRequestDto dto, OrganizationEntity organization) {
        if (dto == null)
            return null;

        return UserEntity.builder()
                .id(dto.getId()) // UUID from Keycloak
                .email(dto.getEmail())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .organization(organization)
                .role(dto.getRole())
                // createdAt handled by @PrePersist
                .build();
    }
}
