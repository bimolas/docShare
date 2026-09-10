package com.subject77.docshare.modules.permission.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionEventPublisher {

    public static final String TOPIC_PERMISSION_EVENTS = "permission-events";

    private final ObjectProvider<KafkaTemplate<String, PermissionChangeEvent>> kafkaTemplateProvider;
    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishPermissionChangeEvent(PermissionChangeEvent event) {
        log.info("Publishing PermissionChangeEvent for permissionId={}, recordHash={}, type={}",
                event.getPermissionId(), event.getRecordHash(), event.getEventType());

        // Publish to internal Spring ApplicationContext listeners
        applicationEventPublisher.publishEvent(event);

        // Publish to Kafka if template bean is present
        KafkaTemplate<String, PermissionChangeEvent> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate != null) {
            try {
                kafkaTemplate.send(TOPIC_PERMISSION_EVENTS, String.valueOf(event.getPermissionId()), event);
            } catch (Exception e) {
                log.error("Failed to publish permission event to Kafka topic {}", TOPIC_PERMISSION_EVENTS, e);
            }
        }
    }
}
