package io.backend.notifications.service;

import io.backend.notifications.client.ExternalMediaClient;
import io.backend.notifications.dto.EnrichedNotificationResponse;
import io.backend.notifications.dto.ExternalPhotoResponse;
import io.backend.notifications.dto.NotificationRequest;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.entity.User;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ExternalMediaClient externalMediaClient;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               ExternalMediaClient externalMediaClient) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.externalMediaClient = externalMediaClient;
    }

    public EnrichedNotificationResponse createNotification(NotificationRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(request.title());
        notification.setContent(request.content());
        notification.setChannel(Channel.valueOf(request.channel()));

        if (request.attachmentIds() != null) {
            notification.setAttachmentIds(request.attachmentIds());
        }

        Notification saved = notificationRepository.save(notification);
        return enrichNotification(saved);
    }

    public EnrichedNotificationResponse getNotificationById(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        return enrichNotification(notification);
    }

    public List<EnrichedNotificationResponse> getNotificationsByUserId(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return notificationRepository.findAllByUserId(userId)
                .stream()
                .map(this::enrichNotification)
                .toList();
    }

    private EnrichedNotificationResponse enrichNotification(Notification notification) {
        List<ExternalPhotoResponse> attachments = resolvePhotos(notification.getAttachmentIds());

        return new EnrichedNotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getChannel().name(),
                notification.getStatus().name(),
                notification.getCreatedAt(),
                notification.getUser().getId(),
                attachments
        );
    }

    private List<ExternalPhotoResponse> resolvePhotos(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
                .map(externalMediaClient::getPhotoById)
                .filter(Objects::nonNull)
                .toList();
    }
}
