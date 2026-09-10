package com.subject77.docshare.modules.channel.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.channel.dto.*;
import com.subject77.docshare.modules.channel.services.ChannelMessageService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/channels/{channelId}/messages")
@RequiredArgsConstructor
public class ChannelMessageController {

    private final ChannelMessageService channelMessageService;

    @PostMapping
    public ResponseEntity<MessageResponseDto> createMessage(
            @PathVariable("channelId") Long channelId,
            @Valid @RequestBody CreateMessageRequestDto request,
            @CurrentUser UserEntity currentUser) {
        MessageResponseDto response = channelMessageService.createMessage(channelId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<MessagePageResponseDto> getMessages(
            @PathVariable("channelId") Long channelId,
            @RequestParam(name = "parentMessageId", required = false) Long parentMessageId,
            @PageableDefault(size = 50) Pageable pageable,
            @CurrentUser UserEntity currentUser) {
        MessagePageResponseDto response = channelMessageService.getMessages(channelId, parentMessageId, pageable, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{messageId}")
    public ResponseEntity<MessageResponseDto> getMessage(
            @PathVariable("channelId") Long channelId,
            @PathVariable("messageId") Long messageId,
            @CurrentUser UserEntity currentUser) {
        MessageResponseDto response = channelMessageService.getMessage(channelId, messageId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<MessageResponseDto> updateMessage(
            @PathVariable("channelId") Long channelId,
            @PathVariable("messageId") Long messageId,
            @Valid @RequestBody UpdateMessageRequestDto request,
            @CurrentUser UserEntity currentUser) {
        MessageResponseDto response = channelMessageService.updateMessage(channelId, messageId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<MessageResponseDto> deleteMessage(
            @PathVariable("channelId") Long channelId,
            @PathVariable("messageId") Long messageId,
            @CurrentUser UserEntity currentUser) {
        MessageResponseDto response = channelMessageService.deleteMessage(channelId, messageId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<MessageReactionResponseDto> addReaction(
            @PathVariable("channelId") Long channelId,
            @PathVariable("messageId") Long messageId,
            @Valid @RequestBody AddMessageReactionRequestDto request,
            @CurrentUser UserEntity currentUser) {
        MessageReactionResponseDto response = channelMessageService.addReaction(channelId, messageId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{messageId}/reactions/{reaction}")
    public ResponseEntity<Void> removeReaction(
            @PathVariable("channelId") Long channelId,
            @PathVariable("messageId") Long messageId,
            @PathVariable("reaction") String reaction,
            @CurrentUser UserEntity currentUser) {
        channelMessageService.removeReaction(channelId, messageId, reaction, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{messageId}/attachments")
    public ResponseEntity<MessageResponseDto> addAttachment(
            @PathVariable("channelId") Long channelId,
            @PathVariable("messageId") Long messageId,
            @RequestParam("documentId") Long documentId,
            @CurrentUser UserEntity currentUser) {
        MessageResponseDto response = channelMessageService.addAttachmentToMessage(channelId, messageId, documentId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{messageId}/attachments/{attachmentId}")
    public ResponseEntity<MessageResponseDto> removeAttachment(
            @PathVariable("channelId") Long channelId,
            @PathVariable("messageId") Long messageId,
            @PathVariable("attachmentId") Long attachmentId,
            @CurrentUser UserEntity currentUser) {
        MessageResponseDto response = channelMessageService.removeAttachmentFromMessage(channelId, messageId, attachmentId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markChannelAsRead(
            @PathVariable("channelId") Long channelId,
            @CurrentUser UserEntity currentUser) {
        channelMessageService.markChannelAsRead(channelId, currentUser);
        return ResponseEntity.ok().build();
    }
}

