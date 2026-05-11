package io.backend.notifications.unit.dto;

import io.backend.notifications.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RegisterRequest")
class RegisterRequestTest {

    @Test
    @DisplayName("should accept optional phone and deviceToken")
    void shouldAcceptOptionalPhoneAndDeviceToken() {
        var req = new RegisterRequest("user1", "u1@test.com", "password123", "+5491112345678", null);
        assertThat(req.phone()).isEqualTo("+5491112345678");
        assertThat(req.deviceToken()).isNull();
    }

    @Test
    @DisplayName("should default phone and deviceToken to null when not provided")
    void shouldDefaultPhoneAndDeviceTokenToNull() {
        var req = new RegisterRequest("user1", "u1@test.com", "password123", null, null);
        assertThat(req.phone()).isNull();
        assertThat(req.deviceToken()).isNull();
    }
}
