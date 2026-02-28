package io.backend.notifications.controller;

import io.backend.notifications.dto.MergedNotificationsResponse;
import io.backend.notifications.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<MergedNotificationsResponse> findByUserId(@PathVariable long id) {
        return ResponseEntity.ok(notificationService.findMergedByUserId(id));
    }

}
