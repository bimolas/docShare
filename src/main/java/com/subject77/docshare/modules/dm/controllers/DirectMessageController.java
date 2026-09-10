package com.subject77.docshare.modules.dm.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.dm.dto.*;
import com.subject77.docshare.modules.dm.services.DirectMessageService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/direct-messages")
@RequiredArgsConstructor
public class DirectMessageController {

    private final DirectMessageService directMessageService;

    @PostMapping("/threads")
    public ResponseEntity<DirectMessageThreadResponseDto> initiateOrGetThread(
            @CurrentUser UserEntity currentUser,
            @Valid @RequestBody InitiateThreadRequestDto request) {
        DirectMessageThreadResponseDto response = directMessageService.initiateOrGetThread(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/threads")
    public ResponseEntity<DirectMessageThreadPageResponseDto> getUserThreads(
            @CurrentUser UserEntity currentUser,
            @PageableDefault(size = 20, sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable) {
        DirectMessageThreadPageResponseDto response = directMessageService.getUserThreads(currentUser, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/threads/{threadId}")
    public ResponseEntity<DirectMessageThreadResponseDto> getThread(
            @CurrentUser UserEntity currentUser,
            @PathVariable("threadId") Long threadId) {
        DirectMessageThreadResponseDto response = directMessageService.getThread(currentUser, threadId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/threads/{threadId}/messages")
    public ResponseEntity<DirectMessagePageResponseDto> getThreadMessages(
            @CurrentUser UserEntity currentUser,
            @PathVariable("threadId") Long threadId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        DirectMessagePageResponseDto response = directMessageService.getThreadMessages(currentUser, threadId, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/threads/{threadId}/messages")
    public ResponseEntity<DirectMessageResponseDto> sendMessage(
            @CurrentUser UserEntity currentUser,
            @PathVariable("threadId") Long threadId,
            @Valid @RequestBody CreateDirectMessageRequestDto request) {
        DirectMessageResponseDto response = directMessageService.sendMessage(currentUser, threadId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/threads/{threadId}/read")
    public ResponseEntity<Void> markThreadAsRead(
            @CurrentUser UserEntity currentUser,
            @PathVariable("threadId") Long threadId) {
        directMessageService.markThreadAsRead(currentUser, threadId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/messages/{messageId}")
    public ResponseEntity<DirectMessageResponseDto> updateMessage(
            @CurrentUser UserEntity currentUser,
            @PathVariable("messageId") Long messageId,
            @Valid @RequestBody UpdateDirectMessageRequestDto request) {
        DirectMessageResponseDto response = directMessageService.updateMessage(currentUser, messageId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @CurrentUser UserEntity currentUser,
            @PathVariable("messageId") Long messageId) {
        directMessageService.deleteMessage(currentUser, messageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/privacy")
    public ResponseEntity<UserMessagePrivacySettingsDto> getPrivacySettings(
            @CurrentUser UserEntity currentUser) {
        UserMessagePrivacySettingsDto response = directMessageService.getPrivacySettings(currentUser);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/privacy")
    public ResponseEntity<UserMessagePrivacySettingsDto> updatePrivacySettings(
            @CurrentUser UserEntity currentUser,
            @Valid @RequestBody UserMessagePrivacySettingsDto dto) {
        UserMessagePrivacySettingsDto response = directMessageService.updatePrivacySettings(currentUser, dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/blocks/{targetUserId}")
    public ResponseEntity<UserBlockResponseDto> blockUser(
            @CurrentUser UserEntity currentUser,
            @PathVariable("targetUserId") String targetUserId) {
        UserBlockResponseDto response = directMessageService.blockUser(currentUser, targetUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/blocks/{targetUserId}")
    public ResponseEntity<Void> unblockUser(
            @CurrentUser UserEntity currentUser,
            @PathVariable("targetUserId") String targetUserId) {
        directMessageService.unblockUser(currentUser, targetUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/blocks")
    public ResponseEntity<List<UserBlockResponseDto>> getBlockedUsers(
            @CurrentUser UserEntity currentUser) {
        List<UserBlockResponseDto> response = directMessageService.getBlockedUsers(currentUser);
        return ResponseEntity.ok(response);
    }
}

