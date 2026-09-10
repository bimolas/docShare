package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.modules.organization.dto.*;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.user.entities.UserEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrganizationService {

    OrganizationResponseDto createOrganization(OrganizationRequestDto request, UserEntity currentUser);

    OrganizationResponseDto getOrganizationByCode(String orgCode, UserEntity currentUser);

    List<OrganizationResponseDto> getUserOrganizations(UserEntity currentUser);

    OrganizationMemberResponseDto addMemberToOrganization(String orgCode, AddMemberRequestDto request, UserEntity currentUser);

    Page<OrganizationMemberResponseDto> getOrganizationMembers(String orgCode, Pageable pageable, UserEntity currentUser);

    List<OrganizationMemberResponseDto> getOrganizationMembers(String orgCode, UserEntity currentUser);

    void removeMemberFromOrganization(String orgCode, String targetUserId, UserEntity currentUser);

    List<OrganizationResponseDto> searchOrganizations(String query, UserEntity currentUser);

    List<OrganizationMemberDetailResponseDto> getOrganizationMemberDetails(String orgCode, MembershipStatus statusFilter, String searchQuery, UserEntity currentUser);

    OrganizationMemberDetailResponseDto getOrganizationMemberDetail(String orgCode, String targetUserId, UserEntity currentUser);

    MemberAccessSummaryResponseDto getOrganizationMemberAccessSummary(String orgCode, String targetUserId, UserEntity currentUser);

    OrganizationMemberDetailResponseDto updateMemberRole(String orgCode, String targetUserId, UpdateMemberRoleRequestDto request, UserEntity currentUser);

    OrganizationMemberDetailResponseDto suspendMember(String orgCode, String targetUserId, MemberStatusChangeRequestDto request, UserEntity currentUser);

    OrganizationMemberDetailResponseDto reactivateMember(String orgCode, String targetUserId, UserEntity currentUser);

    OrganizationMemberDetailResponseDto removeMember(String orgCode, String targetUserId, MemberStatusChangeRequestDto request, UserEntity currentUser);

    OrganizationMemberDetailResponseDto transferOrgAdmin(String orgCode, String newAdminUserId, UserEntity currentUser);
}
