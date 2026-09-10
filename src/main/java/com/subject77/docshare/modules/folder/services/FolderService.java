package com.subject77.docshare.modules.folder.services;

import com.subject77.docshare.modules.folder.dto.BulkMoveRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderContentResponseDto;
import com.subject77.docshare.modules.folder.dto.FolderPeekResponseDto;
import com.subject77.docshare.modules.folder.dto.FolderRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.user.entities.UserEntity;

import java.util.List;

public interface FolderService {

    FolderResponseDto createSubspace(String orgCode, FolderRequestDto request, UserEntity currentUser);

    FolderResponseDto createFolder(String orgCode, FolderRequestDto request, UserEntity currentUser);

    FolderResponseDto getFolderById(Long folderId, UserEntity currentUser);

    FolderContentResponseDto getFolderContents(Long folderId, UserEntity currentUser);

    List<FolderResponseDto> getOrganizationSubspaces(String orgCode, UserEntity currentUser);

    FolderResponseDto createPersonalSubspace(FolderRequestDto request, UserEntity currentUser);

    FolderResponseDto createPersonalFolder(FolderRequestDto request, UserEntity currentUser);

    List<FolderResponseDto> getPersonalSubspaces(UserEntity currentUser);

    void deleteFolder(Long folderId, UserEntity currentUser);

    FolderResponseDto moveFolder(Long folderId, Long targetFolderId, UserEntity currentUser);

    FolderPeekResponseDto getFolderPeek(Long folderId, UserEntity currentUser);

    List<FolderResponseDto> bulkMoveFolders(BulkMoveRequestDto request, UserEntity currentUser);

    FolderResponseDto renameFolder(Long folderId, String newName, UserEntity currentUser);

    void downloadFolderAsZip(Long folderId, UserEntity currentUser, java.io.OutputStream outputStream);
}
