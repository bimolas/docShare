package com.subject77.docshare.modules.folder.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.document.dto.DocumentResponse;
import com.subject77.docshare.modules.document.mapper.DocumentMapper;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.dto.FolderContentResponseDto;
import com.subject77.docshare.modules.folder.dto.FolderRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.folder.dto.*;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.mapper.FolderMapper;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;

import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.workspace.dto.CreateProjectWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.dto.ProjectWorkspaceResponseDto;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.subject77.docshare.modules.document.services.StorageService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.OutputStream;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final FolderRepository folderRepository;
    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final DocumentRepository documentRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;
    private final StorageService storageService;
    private final ProjectWorkspaceRepository projectWorkspaceRepository;
    private final ProjectWorkspaceService projectWorkspaceService;
    private final com.subject77.docshare.modules.policy.services.WorkspacePolicyService workspacePolicyService;
    private final com.subject77.docshare.modules.permission.repository.AccessRequestRepository accessRequestRepository;

    @Override
    @Transactional
    public FolderResponseDto createSubspace(String orgCode, FolderRequestDto request, UserEntity currentUser) {
        CreateProjectWorkspaceRequestDto wsRequest = CreateProjectWorkspaceRequestDto.builder()
                .orgCode(orgCode)
                .name(request.getName() != null ? request.getName().trim() : null)
                .description(null)
                .build();

        ProjectWorkspaceResponseDto createdWs = projectWorkspaceService.createWorkspace(wsRequest, currentUser);
        FolderEntity rootFolder = folderRepository.findById(createdWs.getRootFolderId())
                .orElseThrow(() -> new ResourceNotFoundException("Root folder not found with ID: " + createdWs.getRootFolderId()));

        log.info("Created canonical workspace '{}' (root folder ID: {}) via legacy subspace API in org {} by user {}",
                createdWs.getName(), rootFolder.getId(), orgCode, currentUser.getId());

        return FolderMapper.toResponseDto(createdWs, rootFolder);
    }

    @Override
    @Transactional
    public FolderResponseDto createFolder(String orgCode, FolderRequestDto request, UserEntity currentUser) {
        OrganizationEntity org = organizationRepository.findById(orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with code: " + orgCode));

        validateUserInOrganization(currentUser.getId(), orgCode);

        if (request.getParentFolderId() == null) {
            return createSubspace(orgCode, request, currentUser);
        }

        FolderEntity parentFolder = folderRepository.findById(request.getParentFolderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parent folder not found with ID: " + request.getParentFolderId()));

        if (parentFolder.getOwnerOrg() == null || !parentFolder.getOwnerOrg().getOrgCode().equals(orgCode)) {
            throw new IllegalArgumentException("Parent folder does not belong to organization: " + orgCode);
        }

        if (!permissionEvaluatorService.canEditFolder(currentUser, parentFolder)) {
            throw new AccessDeniedException("User does not have permission to create a subfolder in parent folder ID: " + parentFolder.getId());
        }

        if (folderRepository.existsByNameAndParentFolder_IdAndOwnerOrg_OrgCode(request.getName(), parentFolder.getId(),
                orgCode)) {
            throw new IllegalArgumentException(
                    "Subfolder with name '" + request.getName() + "' already exists in parent folder.");
        }

        FolderEntity folder = FolderEntity.builder()
                .name(request.getName())
                .parentFolder(parentFolder)
                .ownerOrg(org)
                .createdBy(currentUser)
                .build();

        FolderEntity savedFolder = folderRepository.save(folder);
        log.info("Created subfolder '{}' (ID: {}) under parent {} in org {}", savedFolder.getName(),
                savedFolder.getId(), parentFolder.getId(), orgCode);

        Long workspaceId = resolveWorkspaceIdForFolder(parentFolder);
        auditLogService.logDocumentActivity(
                Action.CREATE_FOLDER,
                null,
                "",
                savedFolder.getId(),
                workspaceId,
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getEmail() : "SYSTEM",
                orgCode,
                "0.0.0.0",
                "Internal",
                "Created folder: " + savedFolder.getName(),
                null
        );

        return mapToFolderResponse(savedFolder);
    }

    @Override
    @Transactional(readOnly = true)
    public FolderResponseDto getFolderById(Long folderId, UserEntity currentUser) {
        FolderEntity folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + folderId));

        if (!permissionEvaluatorService.canViewFolder(currentUser, folder)) {
            throw new AccessDeniedException("User does not have permission to view folder ID: " + folderId);
        }

        return mapToFolderResponse(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public FolderContentResponseDto getFolderContents(Long folderId, UserEntity currentUser) {
        FolderEntity currentFolder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + folderId));

        if (!permissionEvaluatorService.canViewFolder(currentUser, currentFolder)) {
            throw new AccessDeniedException("User does not have permission to view folder ID: " + folderId);
        }

        List<FolderBreadcrumbDto> breadcrumbs = new ArrayList<>();
        FolderEntity currBreadcrumb = currentFolder;
        while (currBreadcrumb != null) {
            breadcrumbs.add(0, FolderBreadcrumbDto.builder()
                    .id(currBreadcrumb.getId())
                    .name(currBreadcrumb.getName())
                    .build());
            FolderEntity parent = currBreadcrumb.getParentFolder();
            if (parent != null && !permissionEvaluatorService.canViewFolder(currentUser, parent)) {
                // Stop traversal at first inaccessible ancestor to prevent metadata leakage.
                // The shared folder (or highest accessible ancestor) remains the visible root.
                break;
            }
            currBreadcrumb = parent;
        }

        boolean accessRequestsEnabled = workspacePolicyService.isAccessRequestEnabledForFolder(currentFolder);

        List<FolderEntity> rawSubFolders = folderRepository.findByParentFolder_Id(folderId);
        List<FolderResponseDto> subFolders = new ArrayList<>();
        for (FolderEntity subFolder : rawSubFolders) {
            if (permissionEvaluatorService.canViewFolder(currentUser, subFolder)) {
                FolderResponseDto dto = mapToFolderResponse(subFolder);
                dto.setAccessStatus("ACCESSIBLE");
                dto.setIsLocked(false);
                dto.setRequestStatus("NONE");
                dto.setCanRequestAccess(false);
                subFolders.add(dto);
            } else if (accessRequestsEnabled && currentUser != null && permissionEvaluatorService.hasExplicitExclusion(currentUser, subFolder)) {
                Optional<com.subject77.docshare.modules.permission.entities.AccessRequestEntity> pendingOpt =
                        accessRequestRepository.findFirstByRequester_IdAndFolder_IdAndStatus(
                                currentUser.getId(), subFolder.getId(), com.subject77.docshare.modules.permission.entities.AccessRequestStatus.PENDING);
                boolean isPending = pendingOpt.isPresent();
                FolderResponseDto lockedDto = FolderResponseDto.builder()
                        .id(subFolder.getId())
                        .name(subFolder.getName())
                        .parentFolderId(folderId)
                        .accessStatus("LOCKED")
                        .isLocked(true)
                        .requestStatus(isPending ? "PENDING" : "NONE")
                        .canRequestAccess(!isPending)
                        .pendingRequestId(isPending ? pendingOpt.get().getId() : null)
                        .build();
                subFolders.add(lockedDto);
            }
            // If !canView && (!accessRequestsEnabled || !hasExplicitExclusion): omitted (HIDDEN)
        }

        List<DocumentEntity> rawDocuments = documentRepository.findByFolder_Id(folderId);
        List<DocumentResponse> documents = new ArrayList<>();
        for (DocumentEntity doc : rawDocuments) {
            if (permissionEvaluatorService.canViewDocument(currentUser, doc)) {
                DocumentResponse dto = DocumentMapper.toDocumentResponse(doc);
                dto.setAccessStatus("ACCESSIBLE");
                dto.setIsLocked(false);
                dto.setRequestStatus("NONE");
                dto.setCanRequestAccess(false);
                documents.add(dto);
            } else if (accessRequestsEnabled && currentUser != null && permissionEvaluatorService.hasExplicitExclusion(currentUser, doc)) {
                Optional<com.subject77.docshare.modules.permission.entities.AccessRequestEntity> pendingOpt =
                        accessRequestRepository.findFirstByRequester_IdAndDocument_IdAndStatus(
                                currentUser.getId(), doc.getId(), com.subject77.docshare.modules.permission.entities.AccessRequestStatus.PENDING);
                boolean isPending = pendingOpt.isPresent();
                DocumentResponse lockedDto = DocumentResponse.builder()
                        .id(doc.getId())
                        .fileName(doc.getFileName())
                        .folderId(folderId)
                        .folderName(currentFolder.getName())
                        .accessStatus("LOCKED")
                        .isLocked(true)
                        .requestStatus(isPending ? "PENDING" : "NONE")
                        .canRequestAccess(!isPending)
                        .pendingRequestId(isPending ? pendingOpt.get().getId() : null)
                        .build();
                documents.add(lockedDto);
            }
            // If !canView && (!accessRequestsEnabled || !hasExplicitExclusion): omitted (HIDDEN)
        }

        FolderResponseDto currentFolderDto = mapToFolderResponse(currentFolder);
        currentFolderDto.setAccessStatus("ACCESSIBLE");
        currentFolderDto.setIsLocked(false);
        currentFolderDto.setRequestStatus("NONE");
        currentFolderDto.setCanRequestAccess(false);

        return FolderContentResponseDto.builder()
                .currentFolder(currentFolderDto)
                .breadcrumbs(breadcrumbs)
                .subFolders(subFolders)
                .documents(documents)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponseDto> getOrganizationSubspaces(String orgCode, UserEntity currentUser) {
        List<ProjectWorkspaceResponseDto> workspaces = projectWorkspaceService.getWorkspacesForOrganization(orgCode, currentUser);

        List<ProjectWorkspaceResponseDto> activeWorkspaces = workspaces.stream()
                .filter(w -> w.getStatus() == ProjectWorkspaceStatus.ACTIVE)
                .collect(Collectors.toList());

        if (activeWorkspaces.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> rootFolderIds = activeWorkspaces.stream()
                .map(ProjectWorkspaceResponseDto::getRootFolderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<Long, FolderEntity> rootFolderMap = folderRepository.findAllById(rootFolderIds).stream()
                .collect(Collectors.toMap(FolderEntity::getId, f -> f));

        return activeWorkspaces.stream()
                .map(w -> FolderMapper.toResponseDto(w, rootFolderMap.get(w.getRootFolderId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FolderResponseDto createPersonalSubspace(FolderRequestDto request, UserEntity currentUser) {
        if (folderRepository.existsByNameAndParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(request.getName(),
                currentUser.getId())) {
            throw new IllegalArgumentException(
                    "Personal Subspace root folder with name '" + request.getName() + "' already exists.");
        }

        SubspaceType type = request.getSubspaceType() != null ? request.getSubspaceType() : SubspaceType.PERSONAL;
        FolderEntity subspace = FolderEntity.builder()
                .name(request.getName())
                .subspaceType(type)
                .parentFolder(null)
                .ownerOrg(null)
                .createdBy(currentUser)
                .build();

        FolderEntity savedSubspace = folderRepository.save(subspace);
        log.info("Created Personal Subspace root folder '{}' (ID: {}) by user {}", savedSubspace.getName(),
                savedSubspace.getId(), currentUser.getId());

        auditLogService.logDocumentActivity(
                Action.CREATE_FOLDER,
                null,
                "",
                savedSubspace.getId(),
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getEmail() : "SYSTEM",
                null,
                "0.0.0.0",
                "Internal",
                "Created folder: " + savedSubspace.getName(),
                null
        );

        return mapToFolderResponse(savedSubspace);
    }

    @Override
    @Transactional
    public FolderResponseDto createPersonalFolder(FolderRequestDto request, UserEntity currentUser) {
        if (request.getParentFolderId() == null) {
            return createPersonalSubspace(request, currentUser);
        }

        FolderEntity parentFolder = folderRepository.findById(request.getParentFolderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parent folder not found with ID: " + request.getParentFolderId()));

        if (parentFolder.getOwnerOrg() != null) {
            throw new IllegalArgumentException("Parent folder is an organization folder, not a personal folder.");
        }

        if (!parentFolder.getCreatedBy().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Access denied: You are not the owner of this personal parent folder.");
        }

        if (folderRepository.existsByNameAndParentFolder_IdAndOwnerOrgIsNullAndCreatedBy_Id(request.getName(),
                parentFolder.getId(), currentUser.getId())) {
            throw new IllegalArgumentException(
                    "Personal subfolder with name '" + request.getName() + "' already exists in parent folder.");
        }

        FolderEntity folder = FolderEntity.builder()
                .name(request.getName())
                .parentFolder(parentFolder)
                .ownerOrg(null)
                .createdBy(currentUser)
                .build();

        FolderEntity savedFolder = folderRepository.save(folder);
        log.info("Created personal subfolder '{}' (ID: {}) under parent {} by user {}", savedFolder.getName(),
                savedFolder.getId(), parentFolder.getId(), currentUser.getId());

        auditLogService.logDocumentActivity(
                Action.CREATE_FOLDER,
                null,
                "",
                savedFolder.getId(),
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getEmail() : "SYSTEM",
                null,
                "0.0.0.0",
                "Internal",
                "Created folder: " + savedFolder.getName(),
                null
        );

        return mapToFolderResponse(savedFolder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FolderResponseDto> getPersonalSubspaces(UserEntity currentUser) {
        List<FolderEntity> personalSubspaces = folderRepository
                .findByParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(currentUser.getId());
        return personalSubspaces.stream()
                .map(this::mapToFolderResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteFolder(Long folderId, UserEntity currentUser) {
        FolderEntity folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + folderId));

        if (projectWorkspaceRepository.existsByRootFolder_Id(folderId)) {
            throw new IllegalStateException("Cannot delete folder ID " + folderId + ": It is the root folder of a workspace");
        }

        if (folder.getOwnerOrg() != null) {
            validateUserInOrganization(currentUser.getId(), folder.getOwnerOrg().getOrgCode());
            if (!permissionEvaluatorService.canEditFolder(currentUser, folder)) {
                throw new AccessDeniedException("User does not have permission to delete folder ID: " + folderId);
            }
        } else {
            if (!folder.getCreatedBy().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied: You are not the owner of this personal folder.");
            }
        }

        String orgCode = folder.getOwnerOrg() != null ? folder.getOwnerOrg().getOrgCode() : null;
        Long workspaceId = resolveWorkspaceIdForFolder(folder);
        auditLogService.logDocumentActivity(
                Action.DELETE_FOLDER, null, "", folderId, workspaceId,
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getEmail() : "SYSTEM",
                orgCode, "0.0.0.0", "Internal", "Deleted folder: " + folder.getName(), null);

        folderRepository.delete(folder);
        log.info("Deleted folder {} by user {}", folderId, currentUser.getId());
    }

    @Override
    @Transactional
    public FolderResponseDto moveFolder(Long folderId, Long targetFolderId, UserEntity currentUser) {
        FolderEntity folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + folderId));

        if (projectWorkspaceRepository.existsByRootFolder_Id(folderId)
                || (folder.getParentFolder() == null && folder.getOwnerOrg() != null)) {
            throw new IllegalStateException("Cannot move folder ID " + folderId + ": Workspace root folders cannot be moved");
        }

        if (!permissionEvaluatorService.canEditFolder(currentUser, folder)) {
            throw new AccessDeniedException("User does not have permission to edit/move folder ID: " + folderId);
        }

        FolderEntity targetFolder = null;
        if (targetFolderId != null) {
            if (targetFolderId.equals(folderId)) {
                throw new IllegalArgumentException("Cannot move folder into itself.");
            }
            targetFolder = folderRepository.findById(targetFolderId)
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Target folder not found with ID: " + targetFolderId));

            if (!permissionEvaluatorService.canEditFolder(currentUser, targetFolder)) {
                throw new AccessDeniedException(
                        "User does not have permission to write/move into target folder ID: " + targetFolderId);
            }

            if (isSameOrDescendant(targetFolder, folderId)) {
                throw new IllegalArgumentException(
                        "Cannot move folder into one of its own subfolders (circular dependency detected).");
            }
        }

        folder.setParentFolder(targetFolder);
        FolderEntity saved = folderRepository.save(folder);

        String orgCode = saved.getOwnerOrg() != null ? saved.getOwnerOrg().getOrgCode() : null;
        Long workspaceId = resolveWorkspaceIdForFolder(targetFolder != null ? targetFolder : saved);
        auditLogService.logDocumentActivity(
                Action.MOVE_FOLDER, null, "", saved.getId(), workspaceId,
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getEmail() : "SYSTEM",
                orgCode, "0.0.0.0", "Internal", "Moved folder to target folder ID: " + targetFolderId, null);

        log.info("Moved folder ID {} to target folder ID {} by user {}", folderId, targetFolderId, currentUser.getId());
        return mapToFolderResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FolderPeekResponseDto getFolderPeek(Long folderId, UserEntity currentUser) {
        FolderEntity folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + folderId));

        if (!permissionEvaluatorService.canViewFolder(currentUser, folder)) {
            throw new AccessDeniedException("User does not have permission to view folder ID: " + folderId);
        }

        List<FolderEntity> subFolders = folderRepository.findByParentFolder_Id(folderId);
        List<DocumentEntity> documents = documentRepository.findByFolder_Id(folderId);

        long totalSize = documents.stream()
                .mapToLong(d -> d.getFileSize() != null ? d.getFileSize() : 0L)
                .sum();

        List<QuickItemPreviewDto> topItems = new ArrayList<>();
        for (FolderEntity sf : subFolders) {
            topItems.add(QuickItemPreviewDto.builder()
                    .id(sf.getId())
                    .name(sf.getName())
                    .itemType("FOLDER")
                    .sizeInBytes(null)
                    .modifiedAt(sf.getCreatedAt())
                    .build());
        }
        for (DocumentEntity doc : documents) {
            topItems.add(QuickItemPreviewDto.builder()
                    .id(doc.getId())
                    .name(doc.getFileName())
                    .itemType("DOCUMENT")
                    .sizeInBytes(doc.getFileSize())
                    .modifiedAt(doc.getUploadedAt())
                    .build());
        }

        List<QuickItemPreviewDto> previewList = topItems.stream()
                .filter(i -> i.getModifiedAt() != null)
                .sorted(Comparator.comparing(QuickItemPreviewDto::getModifiedAt).reversed())
                .limit(5)
                .collect(Collectors.toList());

        PermissionLevel permLevel = permissionEvaluatorService.resolveMaxPermissionLevel(currentUser, folder);

        return FolderPeekResponseDto.builder()
                .folderId(folder.getId())
                .folderName(folder.getName())
                .parentFolderId(folder.getParentFolder() != null ? folder.getParentFolder().getId() : null)
                .ownerOrgCode(folder.getOwnerOrg() != null ? folder.getOwnerOrg().getOrgCode() : null)
                .createdByUserId(folder.getCreatedBy() != null ? folder.getCreatedBy().getId() : null)
                .createdAt(folder.getCreatedAt())
                .subfolderCount(subFolders.size())
                .fileCount(documents.size())
                .totalSizeBytes(totalSize)
                .currentUserPermission(permLevel)
                .topItems(previewList)
                .build();
    }

    @Override
    @Transactional
    public List<FolderResponseDto> bulkMoveFolders(BulkMoveRequestDto request, UserEntity currentUser) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            return List.of();
        }
        List<FolderResponseDto> moved = new ArrayList<>();
        for (Long folderId : request.getIds()) {
            moved.add(moveFolder(folderId, request.getTargetFolderId(), currentUser));
        }
        return moved;
    }

    @Override
    @Transactional
    public FolderResponseDto renameFolder(Long folderId, String newName, UserEntity currentUser) {
        FolderEntity folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + folderId));

        if (projectWorkspaceRepository.existsByRootFolder_Id(folderId)) {
            throw new IllegalStateException("Cannot rename a workspace root folder directly; rename the workspace instead.");
        }

        if (!permissionEvaluatorService.canEditFolder(currentUser, folder)) {
            throw new AccessDeniedException("User does not have permission to rename folder ID: " + folderId);
        }

        String oldName = folder.getName();
        folder.setName(newName);
        FolderEntity saved = folderRepository.save(folder);

        String orgCode = saved.getOwnerOrg() != null ? saved.getOwnerOrg().getOrgCode() : null;
        Long workspaceId = resolveWorkspaceIdForFolder(saved);
        auditLogService.logDocumentActivity(
                Action.RENAME_METADATA, null, "", saved.getId(), workspaceId,
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getEmail() : "SYSTEM",
                orgCode, "0.0.0.0", "Internal", "Renamed folder from '" + oldName + "' to '" + newName + "'", null);

        log.info("Renamed folder ID {} from '{}' to '{}' by user {}", folderId, oldName, newName, currentUser.getId());
        return mapToFolderResponse(saved);
    }

    private boolean isSameOrDescendant(FolderEntity targetFolder, Long sourceFolderId) {
        FolderEntity current = targetFolder;
        while (current != null) {
            if (current.getId() != null && current.getId().equals(sourceFolderId)) {
                return true;
            }
            current = current.getParentFolder();
        }
        return false;
    }

    private void validateUserInOrganization(String userId, String orgCode) {
        if (!userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode(userId, orgCode)) {
            throw new AccessDeniedException("User is not a member of organization: " + orgCode);
        }
    }

    private FolderResponseDto mapToFolderResponse(FolderEntity folder) {
        return FolderMapper.toResponseDto(folder);
    }

    @Override
    @Transactional(readOnly = true)
    public void downloadFolderAsZip(Long folderId, UserEntity currentUser, OutputStream outputStream) {
        FolderEntity rootFolder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + folderId));

        if (!permissionEvaluatorService.canDownloadFolder(currentUser, rootFolder)) {
            throw new AccessDeniedException("User does not have permission to download folder ID: " + folderId);
        }

        String orgCode = rootFolder.getOwnerOrg() != null ? rootFolder.getOwnerOrg().getOrgCode() : null;
        Long workspaceId = resolveWorkspaceIdForFolder(rootFolder);
        auditLogService.logDocumentActivity(
                Action.DOWNLOAD,
                null,
                "",
                rootFolder.getId(),
                workspaceId,
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getEmail() : "SYSTEM",
                orgCode,
                "0.0.0.0",
                "Internal",
                "Downloaded folder: " + rootFolder.getName(),
                null
        );

        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            zipFolderContents(rootFolder, "", zos);
            zos.finish();
        } catch (IOException e) {
            log.error("Error creating ZIP stream for folder ID {}", folderId, e);
            throw new RuntimeException("Failed to generate folder zip", e);
        }
    }

    private void zipFolderContents(FolderEntity folder, String currentPath, ZipOutputStream zos) throws IOException {
        String basePath = currentPath.isEmpty() ? folder.getName() + "/" : currentPath + folder.getName() + "/";

        // Add empty folder entry to zip
        zos.putNextEntry(new ZipEntry(basePath));
        zos.closeEntry();

        List<DocumentEntity> documents = documentRepository.findByFolder_Id(folder.getId());
        for (DocumentEntity doc : documents) {
            String filePath = basePath + doc.getFileName();
            zos.putNextEntry(new ZipEntry(filePath));
            try {
                storageService.streamObject(doc.getS3Bucket(), doc.getObjectKey(), zos);
            } catch (Exception e) {
                log.error("Failed to stream document {} to zip", doc.getId(), e);
            }
            zos.closeEntry();
        }

        List<FolderEntity> subFolders = folderRepository.findByParentFolder_Id(folder.getId());
        for (FolderEntity subFolder : subFolders) {
            zipFolderContents(subFolder, basePath, zos);
        }
    }

    private Long resolveWorkspaceIdForFolder(FolderEntity folder) {
        if (folder == null) return null;
        FolderEntity cursor = folder;
        while (cursor.getParentFolder() != null) {
            cursor = cursor.getParentFolder();
        }
        if (cursor.getId() != null) {
            return projectWorkspaceRepository.findFirstByRootFolder_Id(cursor.getId())
                    .map(com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity::getId)
                    .orElse(null);
        }
        return null;
    }
}
