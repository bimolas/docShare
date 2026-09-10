package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.permission.dto.BulkSelectedMemberShareDto;
import com.subject77.docshare.modules.permission.dto.BulkShareWithExclusionsDto;
import com.subject77.docshare.modules.permission.dto.EffectiveAccessAuditDto;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.dto.SharedWithMeResponseDto;
import com.subject77.docshare.modules.permission.dto.SharedByMeResponseDto;
import com.subject77.docshare.modules.permission.dto.PermissionUpdateRequestDto;
import com.subject77.docshare.modules.user.entities.UserEntity;
import java.util.List;

import com.subject77.docshare.modules.permission.dto.ManageAccessSummaryDto;

public interface PermissionService {

    PermissionResponseDto grantPermission(PermissionRequestDto requestDto, String grantedByUserId);

    List<PermissionResponseDto> grantFolderShareWithExclusions(BulkShareWithExclusionsDto requestDto, String grantedByUserId);

    List<PermissionResponseDto> grantSelectedMembers(BulkSelectedMemberShareDto requestDto, String grantedByUserId);

    PermissionResponseDto updatePermission(Long permissionId, PermissionUpdateRequestDto requestDto, String updatedByUserId);

    PermissionResponseDto revokePermission(Long permissionId, String revokedByUserId);

    PermissionResponseDto activatePermission(Long permissionId, String activatedByUserId);

    PermissionResponseDto suspendPermission(Long permissionId, String suspendedByUserId);

    PermissionResponseDto getPermissionById(Long permissionId, UserEntity currentUser);
    default PermissionResponseDto getPermissionById(Long permissionId) {
        return getPermissionById(permissionId, null);
    }

    List<PermissionResponseDto> getActivePermissionsForDocument(Long documentId, UserEntity currentUser);
    default List<PermissionResponseDto> getActivePermissionsForDocument(Long documentId) {
        return getActivePermissionsForDocument(documentId, null);
    }

    List<PermissionResponseDto> getActivePermissionsForFolder(Long folderId, UserEntity currentUser);
    default List<PermissionResponseDto> getActivePermissionsForFolder(Long folderId) {
        return getActivePermissionsForFolder(folderId, null);
    }

    List<PermissionResponseDto> getPermissionsForTarget(String targetUserEmail, String targetOrgCode, Long targetGroupId, UserEntity currentUser);
    default List<PermissionResponseDto> getPermissionsForTarget(String targetUserEmail, String targetOrgCode, Long targetGroupId) {
        return getPermissionsForTarget(targetUserEmail, targetOrgCode, targetGroupId, null);
    }

    List<PermissionResponseDto> getActivePermissionsForGroup(Long groupId, UserEntity currentUser);
    default List<PermissionResponseDto> getActivePermissionsForGroup(Long groupId) {
        return getActivePermissionsForGroup(groupId, null);
    }

    SharedWithMeResponseDto getSharedWithMe(UserEntity currentUser);

    SharedByMeResponseDto getSharedByMe(UserEntity currentUser, boolean includeInactive);
    default SharedByMeResponseDto getSharedByMe(UserEntity currentUser) {
        return getSharedByMe(currentUser, true);
    }

    EffectiveAccessAuditDto getEffectivePermissions(Long targetId, String targetType, UserEntity currentUser);

    ManageAccessSummaryDto getManageAccessSummary(Long resourceId, String resourceType, UserEntity currentUser);
}



