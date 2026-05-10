package io.backend.notifications.unit.service.channel;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.service.channel.PushChannelSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PushChannelSender}.
 *
 * Tests R4 from spec:
 * - Formats a JSON payload containing title and content
 * - Logs "Push notification dispatched: {payload}"
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PushChannelSender")
class PushChannelSenderTest {

    @InjectMocks
    private PushChannelSender pushChannelSender;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(PushChannelSender.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Test
    @DisplayName("should log push notification with JSON payload containing title and content")
    void shouldLogPushNotificationWithJsonPayload() {
        Notification notification = mock(Notification.class);
        when(notification.getTitle()).thenReturn("Alert");
        when(notification.getContent()).thenReturn("Server updated");

        pushChannelSender.send(notification);

        assertThat(listAppender.list)
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage())
                            .contains("Push notification dispatched")
                            .contains("{\"title\":\"Alert\",\"content\":\"Server updated\"}");
                });
    }
}
