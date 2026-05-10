package io.backend.notifications.unit.service.channel;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.backend.notifications.entity.Notification;
import io.backend.notifications.service.channel.SmsChannelSender;
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
 * Unit tests for {@link SmsChannelSender}.
 *
 * Tests R3 from spec:
 * - Content within 160 characters is NOT truncated
 * - Content exceeding 160 characters IS truncated to 160 before logging
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmsChannelSender")
class SmsChannelSenderTest {

    @InjectMocks
    private SmsChannelSender smsChannelSender;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(SmsChannelSender.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Test
    @DisplayName("should log full content when within 160 character limit")
    void shouldNotTruncateContentWithinLimit() {
        String content = "Hello World";
        Notification notification = mock(Notification.class);
        when(notification.getContent()).thenReturn(content);

        smsChannelSender.send(notification);

        assertThat(listAppender.list)
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage())
                            .contains("Hello World"); // proves content is fully present, not truncated
                });
    }

    @Test
    @DisplayName("should truncate content to 160 characters when limit exceeded")
    void shouldTruncateContentExceedingLimit() {
        String content = "A".repeat(200);
        Notification notification = mock(Notification.class);
        when(notification.getContent()).thenReturn(content);

        smsChannelSender.send(notification);

        assertThat(listAppender.list)
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    String message = event.getFormattedMessage();
                    // Should contain truncated content (exactly 160 chars)
                    assertThat(message).contains("A".repeat(160));
                    // Should NOT contain the full 200-char content
                    assertThat(message).doesNotContain("A".repeat(161));
                });
    }

    @Test
    @DisplayName("should log content exactly at 160 characters without truncation")
    void shouldNotTruncateContentAtExactLimit() {
        String content = "B".repeat(160);
        Notification notification = mock(Notification.class);
        when(notification.getContent()).thenReturn(content);

        smsChannelSender.send(notification);

        assertThat(listAppender.list)
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    String message = event.getFormattedMessage();
                    assertThat(message).contains("B".repeat(160));
                });
    }
}
