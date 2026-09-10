package com.subject77.docshare.modules.notification.services;

import com.subject77.docshare.modules.notification.dto.NotificationResponseDto;
import com.subject77.docshare.modules.notification.dto.UnreadCountResponseDto;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationStatus;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.user.entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    NotificationEntity createNotification(UserEntity recipient, UserEntity actor, NotificationType type, String title, String body, NotificationSourceType sourceType, String sourceId);

    NotificationEntity createNotification(UserEntity recipient, UserEntity actor, NotificationType type, String title, String body, NotificationSourceType sourceType, String sourceId, NotificationCategory category, NotificationPriority priority, String idempotencyKey);

    Page<NotificationResponseDto> getUserNotifications(UserEntity currentUser, NotificationStatus status, NotificationSourceType sourceType, Pageable pageable);

    Page<NotificationResponseDto> getUserNotifications(UserEntity currentUser, NotificationStatus status, NotificationSourceType sourceType, NotificationCategory category, Pageable pageable);

    UnreadCountResponseDto getUnreadCount(UserEntity currentUser);

    NotificationResponseDto markAsRead(Long notificationId, UserEntity currentUser);

    NotificationResponseDto markAsUnread(Long notificationId, UserEntity currentUser);

    void markAllAsRead(UserEntity currentUser);

    NotificationResponseDto archiveNotification(Long notificationId, UserEntity currentUser);

    NotificationResponseDto unarchiveNotification(Long notificationId, UserEntity currentUser);

    void deleteNotification(Long notificationId, UserEntity currentUser);
}

