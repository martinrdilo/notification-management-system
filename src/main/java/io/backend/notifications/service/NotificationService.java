package io.backend.notifications.service;

import io.backend.notifications.client.ExternalPostClient;
import io.backend.notifications.dto.AppNotificationResponse;
import io.backend.notifications.dto.ExternalPostResponse;
import io.backend.notifications.dto.MergedNotificationsResponse;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.entity.User;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ExternalPostClient externalPostClient;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               ExternalPostClient externalPostClient) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.externalPostClient = externalPostClient;
    }

    public List<Notification> findByUserId(Long userId) {
        findUserById(userId);
        return notificationRepository.findAllByUserId(userId);
    }

    public MergedNotificationsResponse findMergedByUserId(Long userId) {
        findUserById(userId);

        List<AppNotificationResponse> appNotifications = notificationRepository.findAllByUserId(userId)
                .stream()
                .map(this::toAppNotificationResponse)
                .toList();

        List<ExternalPostResponse> externalNotifications;
        try {
            externalNotifications = externalPostClient.getPostsByUser(userId);
        } catch (RuntimeException ignored) {
            externalNotifications = List.of();
        }

        return new MergedNotificationsResponse(appNotifications, externalNotifications != null ? externalNotifications : List.of());
    }

    private AppNotificationResponse toAppNotificationResponse(Notification notification) {
        return new AppNotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getChannel(),
                notification.getStatus(),
                notification.getCreatedAt()
        );
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

}
