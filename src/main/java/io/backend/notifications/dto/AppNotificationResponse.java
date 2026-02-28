package io.backend.notifications.dto;

import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;

import java.time.LocalDateTime;

public record AppNotificationResponse(
        Long id,
        String title,
        String content,
        Channel channel,
        Status status,
        LocalDateTime createdAt
) {}
