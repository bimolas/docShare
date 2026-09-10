package com.subject77.docshare.modules.notification.events;

import com.subject77.docshare.modules.notification.dto.NotificationResponseDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NotificationCreatedEvent extends ApplicationEvent {

    private final NotificationResponseDto notification;

    public NotificationCreatedEvent(Object source, NotificationResponseDto notification) {
        super(source);
        this.notification = notification;
    }

    public NotificationCreatedEvent(NotificationResponseDto notification) {
        super(notification != null ? notification : "NotificationCreatedEvent");
        this.notification = notification;
    }
}
