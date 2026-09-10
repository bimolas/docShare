package com.subject77.docshare.modules.notification.dto;

import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationStatus;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDto {
    private Long id;
    private String recipientId;
    private String actorId;
    private String actorName;
    private NotificationType type;
    private NotificationCategory category;
    private NotificationPriority priority;
    private String title;
    private String body;
    private NotificationStatus status;
    private NotificationSourceType sourceType;
    private String sourceId;
    private Instant createdAt;
    private Instant readAt;
    private Instant archivedAt;
}

