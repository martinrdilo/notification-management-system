package io.backend.notifications.integration.controller;

import io.backend.notifications.dto.UserRequest;
import io.backend.notifications.entity.User;
import io.backend.notifications.fixture.entity.UserBuilder;
import io.backend.notifications.integration.base.AbstractIntegrationTest;
import io.backend.notifications.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for UserController.
 * Proves full stack works: HTTP request -> Controller -> Service -> Repository -> DB.
 * POST /users has been removed — user creation is via POST /auth/register.
 */
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        cleanDatabase();
    }

    @Test
    void shouldGetAllUsers() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        webTestClient().get()
                .uri("/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray();
    }

    @Test
    void shouldGetUserById() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        User user = userRepository.findByEmail(builder.getEmail()).orElseThrow();

        webTestClient().get()
                .uri("/users/{id}", user.getId())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo(user.getUsername())
                .jsonPath("$.email").isEqualTo(user.getEmail());
    }

    @Test
    void shouldReturn404WhenUserNotFound() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        webTestClient().get()
                .uri("/users/{id}", 999)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldReturn401WhenNoTokenOnGetUsers() {
        webTestClient().get()
                .uri("/users")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldReturn401WhenNoTokenOnGetUserById() {
        webTestClient().get()
                .uri("/users/{id}", 1)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldDeleteUser() {
        UserBuilder builder = UserBuilder.aUser();
        String token = registerAndLogin(builder);

        User user = userRepository.findByEmail(builder.getEmail()).orElseThrow();

        webTestClient().delete()
                .uri("/users/{id}", user.getId())
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isNoContent();

        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void shouldReturn401WhenNoTokenOnDelete() {
        webTestClient().delete()
                .uri("/users/{id}", 1)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
