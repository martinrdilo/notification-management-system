package io.backend.notifications.service;

import io.backend.notifications.client.ExternalMediaClient;
import io.backend.notifications.dto.EnrichedNotificationResponse;
import io.backend.notifications.dto.ExternalPhotoResponse;
import io.backend.notifications.dto.NotificationRequest;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.entity.User;
import io.backend.notifications.enums.Channel;
import io.backend.notifications.enums.Status;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.repository.UserRepository;
import io.backend.notifications.service.channel.ChannelDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ExternalMediaClient externalMediaClient;
    private final ChannelDispatcher channelDispatcher;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               ExternalMediaClient externalMediaClient,
                               ChannelDispatcher channelDispatcher) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.externalMediaClient = externalMediaClient;
        this.channelDispatcher = channelDispatcher;
    }

    public EnrichedNotificationResponse createNotification(NotificationRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(request.title());
        notification.setContent(request.content());
        notification.setChannel(Channel.valueOf(request.channel()));

        if (request.attachmentIds() != null) {
            notification.setAttachmentIds(request.attachmentIds());
        }

        Notification saved = notificationRepository.save(notification);
        try {
            channelDispatcher.dispatch(saved);
            saved.setStatus(Status.SENT);
        } catch (Exception e) {
            log.error("Channel dispatch failed: {}", e.getMessage());
            saved.setStatus(Status.FAILED);
        }
        notificationRepository.save(saved);
        return enrichNotification(saved);
    }

    public EnrichedNotificationResponse getNotificationById(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        return enrichNotification(notification);
    }

    public List<EnrichedNotificationResponse> getNotificationsByUserId(Long userId) {
        User requestedUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String authenticatedEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (!requestedUser.getEmail().equals(authenticatedEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

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
