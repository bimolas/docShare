package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.document.dto.ConfirmUploadRequest;
import com.subject77.docshare.modules.document.dto.DocumentResponse;
import com.subject77.docshare.modules.document.dto.InitiateUploadRequest;
import com.subject77.docshare.modules.document.dto.InitiateUploadResponse;
import com.subject77.docshare.modules.permission.dto.FileRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.FileRequestResponseDto;
import com.subject77.docshare.modules.user.entities.UserEntity;

import java.util.List;

public interface FileRequestService {

    FileRequestResponseDto createFileRequest(FileRequestCreateDto dto, UserEntity currentUser);

    List<FileRequestResponseDto> getRequestsMine(UserEntity currentUser);

    List<FileRequestResponseDto> getRequestsReceived(UserEntity currentUser);

    FileRequestResponseDto getFileRequestById(Long id, UserEntity currentUser);

    InitiateUploadResponse initiateUpload(Long requestId, InitiateUploadRequest request, UserEntity currentUser);

    DocumentResponse confirmUpload(Long requestId, ConfirmUploadRequest request, UserEntity currentUser);

    FileRequestResponseDto completeFileRequest(Long requestId, UserEntity currentUser);

    FileRequestResponseDto cancelFileRequest(Long requestId, UserEntity currentUser);
}
