package com.subject77.docshare.modules.channel.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.channel.dto.*;
import com.subject77.docshare.modules.channel.services.ChannelMessageService;
import com.subject77.docshare.modules.channel.services.ChannelService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;
    private final ChannelMessageService channelMessageService;

    @PostMapping("/{channelId}/read")
    public ResponseEntity<Void> markChannelAsRead(
            @PathVariable("channelId") Long channelId,
            @CurrentUser UserEntity currentUser) {
        channelMessageService.markChannelAsRead(channelId, currentUser);
        return ResponseEntity.ok().build();
    }


    @PostMapping
    public ResponseEntity<ChannelResponseDto> createChannel(
            @Valid @RequestBody CreateChannelRequestDto request,
            @CurrentUser UserEntity currentUser) {
        ChannelResponseDto response = channelService.createChannel(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{channelId}")
    public ResponseEntity<ChannelResponseDto> getChannel(
            @PathVariable("channelId") Long channelId,
            @CurrentUser UserEntity currentUser) {
        ChannelResponseDto response = channelService.getChannel(channelId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ChannelResponseDto>> listChannels(
            @RequestParam(name = "orgCode", required = false) String orgCode,
            @RequestParam(name = "teamId", required = false) Long teamId,
            @RequestParam(name = "projectWorkspaceId", required = false) Long projectWorkspaceId,
            @RequestParam(name = "collaborationId", required = false) Long collaborationId,
            @CurrentUser UserEntity currentUser) {
        List<ChannelResponseDto> response = channelService.listChannels(orgCode, teamId, projectWorkspaceId, collaborationId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{channelId}")
    public ResponseEntity<ChannelResponseDto> updateChannel(
            @PathVariable("channelId") Long channelId,
            @Valid @RequestBody UpdateChannelRequestDto request,
            @CurrentUser UserEntity currentUser) {
        ChannelResponseDto response = channelService.updateChannel(channelId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{channelId}/archive")
    public ResponseEntity<ChannelResponseDto> archiveChannel(
            @PathVariable("channelId") Long channelId,
            @CurrentUser UserEntity currentUser) {
        ChannelResponseDto response = channelService.archiveChannel(channelId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{channelId}/members")
    public ResponseEntity<ChannelMemberResponseDto> addMember(
            @PathVariable("channelId") Long channelId,
            @Valid @RequestBody AddChannelMemberRequestDto request,
            @CurrentUser UserEntity currentUser) {
        ChannelMemberResponseDto response = channelService.addMember(channelId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{channelId}/members")
    public ResponseEntity<List<ChannelMemberResponseDto>> listMembers(
            @PathVariable("channelId") Long channelId,
            @CurrentUser UserEntity currentUser) {
        List<ChannelMemberResponseDto> response = channelService.listMembers(channelId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{channelId}/members/{userId}/role")
    public ResponseEntity<ChannelMemberResponseDto> updateMemberRole(
            @PathVariable("channelId") Long channelId,
            @PathVariable("userId") String userId,
            @Valid @RequestBody UpdateChannelMemberRoleRequestDto request,
            @CurrentUser UserEntity currentUser) {
        ChannelMemberResponseDto response = channelService.updateMemberRole(channelId, userId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{channelId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable("channelId") Long channelId,
            @PathVariable("userId") String userId,
            @CurrentUser UserEntity currentUser) {
        channelService.removeMember(channelId, userId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
