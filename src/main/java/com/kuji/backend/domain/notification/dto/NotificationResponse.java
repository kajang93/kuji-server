package com.kuji.backend.domain.notification.dto;

import com.kuji.backend.domain.notification.entity.Notification;
import com.kuji.backend.domain.notification.entity.NotificationType;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class NotificationResponse {
    
    private Long id;
    private String title;
    private String body;
    private NotificationType type;
    private String targetId;
    private boolean isRead;
    private LocalDateTime createdAt;

    @Builder
    public NotificationResponse(Long id, String title, String body, NotificationType type, String targetId, boolean isRead, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.type = type;
        this.targetId = targetId;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .body(notification.getBody())
                .type(notification.getType())
                .targetId(notification.getTargetId())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
