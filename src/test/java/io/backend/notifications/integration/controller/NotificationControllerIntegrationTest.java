package io.backend.notifications.integration.controller;

import io.backend.notifications.dto.NotificationRequest;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import io.backend.notifications.repository.NotificationRepository;
import io.backend.notifications.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;

/**
 * Integration tests for NotificationController with JWT authentication.
 * Tests: create (authenticated), 401 without token, 403 for cross-user access.
 */
class NotificationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @Test
    void shouldCreateNotificationWhenAuthenticated() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        NotificationRequest request = new NotificationRequest(
                "Test Title",
                "Test Content",
                "EMAIL",
                List.of()
        );

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Test Title")
                .jsonPath("$.channel").isEqualTo("EMAIL");
    }

    @Test
    void shouldReturn401WhenCreatingNotificationWithoutToken() {
        NotificationRequest request = new NotificationRequest(
                "Test Title",
                "Test Content",
                "EMAIL",
                List.of()
        );

        webTestClient().post()
                .uri("/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldReturn401WhenGettingNotificationsWithoutToken() {
        webTestClient().get()
                .uri("/notifications/user/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldGetOwnNotifications() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        // Get user id
        var user = userRepository.findByEmail(builder.getEmail()).orElseThrow();

        // Create a notification first
        NotificationRequest request = new NotificationRequest(
                "My Notification",
                "My Content",
                "SMS",
                List.of()
        );

        webTestClient().post()
                .uri("/notifications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated();

        // Retrieve own notifications
        webTestClient().get()
                .uri("/notifications/user/{userId}", user.getId())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].title").isEqualTo("My Notification");
    }

    @Test
    void shouldReturn403WhenAccessingAnotherUsersNotifications() {
        // User A
        UserBuilder builderA = UserBuilder.aUser();
        String tokenA = registerAndLogin(builderA);

        // User B
        UserBuilder builderB = UserBuilder.aUser();
        registerAndLogin(builderB);
        var userB = userRepository.findByEmail(builderB.getEmail()).orElseThrow();

        // User A tries to access User B's notifications
        webTestClient().get()
                .uri("/notifications/user/{userId}", userB.getId())
                .header("Authorization", "Bearer " + tokenA)
                .exchange()
                .expectStatus().isForbidden();
    }
}
