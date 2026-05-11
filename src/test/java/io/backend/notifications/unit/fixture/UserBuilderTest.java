package io.backend.notifications.unit.fixture;

import io.backend.notifications.fixture.entity.UserBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserBuilder")
class UserBuilderTest {

    @Test
    @DisplayName("should include phone and deviceToken in RegisterRequest when set")
    void shouldIncludePhoneAndDeviceTokenInRegisterRequest() {
        var reg = UserBuilder.aUser()
                .withPhone("+5491112345678")
                .withDeviceToken("tok_abc123")
                .buildRegisterRequest();

        assertThat(reg.phone()).isEqualTo("+5491112345678");
        assertThat(reg.deviceToken()).isEqualTo("tok_abc123");
    }

    @Test
    @DisplayName("should default phone and deviceToken to null in RegisterRequest")
    void shouldDefaultPhoneAndDeviceTokenToNullInRegisterRequest() {
        var reg = UserBuilder.aUser().buildRegisterRequest();

        assertThat(reg.phone()).isNull();
        assertThat(reg.deviceToken()).isNull();
    }
}
