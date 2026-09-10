package com.subject77.docshare.modules.notification.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.notification.dto.NotificationResponseDto;
import com.subject77.docshare.modules.notification.dto.UnreadCountResponseDto;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationStatus;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NotificationResponseDto>> getUserNotifications(
            @CurrentUser UserEntity currentUser,
            @RequestParam(value = "status", required = false) NotificationStatus status,
            @RequestParam(value = "sourceType", required = false) NotificationSourceType sourceType,
            @RequestParam(value = "category", required = false) NotificationCategory category,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<NotificationResponseDto> response = notificationService.getUserNotifications(currentUser, status, sourceType, category, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UnreadCountResponseDto> getUnreadCount(
            @CurrentUser UserEntity currentUser) {
        UnreadCountResponseDto response = notificationService.getUnreadCount(currentUser);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationResponseDto> markAsRead(
            @CurrentUser UserEntity currentUser,
            @PathVariable("id") Long id) {
        NotificationResponseDto response = notificationService.markAsRead(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/unread")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationResponseDto> markAsUnread(
            @CurrentUser UserEntity currentUser,
            @PathVariable("id") Long id) {
        NotificationResponseDto response = notificationService.markAsUnread(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllAsRead(
            @CurrentUser UserEntity currentUser) {
        notificationService.markAllAsRead(currentUser);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationResponseDto> archiveNotification(
            @CurrentUser UserEntity currentUser,
            @PathVariable("id") Long id) {
        NotificationResponseDto response = notificationService.archiveNotification(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/unarchive")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationResponseDto> unarchiveNotification(
            @CurrentUser UserEntity currentUser,
            @PathVariable("id") Long id) {
        NotificationResponseDto response = notificationService.unarchiveNotification(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteNotification(
            @CurrentUser UserEntity currentUser,
            @PathVariable("id") Long id) {
        notificationService.deleteNotification(id, currentUser);
        return ResponseEntity.noContent().build();
    }
}
