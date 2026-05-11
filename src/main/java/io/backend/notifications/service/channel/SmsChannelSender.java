package io.backend.notifications.service.channel;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Sends notifications via SMS.
 * Truncates content to 160 characters if it exceeds the limit.
 */
@Component
public class SmsChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(SmsChannelSender.class);

    @Override
    public void send(Notification notification) {
        String content = notification.getContent();
        String truncated = content.substring(0, Math.min(content.length(), 160));
        String phone = notification.getUser().getPhone();
        if (phone != null) {
            log.info("SMS sent to {} at {}: {}", phone, Instant.now(), truncated);
        } else {
            log.warn("No phone number for user {}", notification.getUser().getId());
        }
    }

    @Override
    public Channel getChannel() {
        return Channel.SMS;
    }
}
