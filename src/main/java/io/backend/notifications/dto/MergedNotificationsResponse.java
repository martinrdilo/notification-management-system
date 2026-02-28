package io.backend.notifications.dto;

import java.util.List;

public record MergedNotificationsResponse(
        List<AppNotificationResponse> appNotifications,
        List<ExternalPostResponse> externalNotifications
) {}
