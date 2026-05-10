package io.backend.notifications.service.channel;

import io.backend.notifications.entity.Notification;
import io.backend.notifications.enums.Channel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dispatches notifications to the appropriate {@link ChannelSender}
 * based on the notification's {@link Channel}.
 *
 * Collects all {@link ChannelSender} beans via Spring constructor injection
 * and builds a lookup map keyed by {@link Channel}.
 */
@Service
public class ChannelDispatcher {

    private final Map<Channel, ChannelSender> senderMap;

    public ChannelDispatcher(List<ChannelSender> senders) {
        this.senderMap = senders.stream()
                .collect(Collectors.toMap(ChannelSender::getChannel, Function.identity()));
    }

    /**
     * Dispatch a notification to the sender registered for its channel.
     *
     * @param notification the notification to dispatch
     * @throws IllegalStateException if no sender is registered for the notification's channel
     */
    public void dispatch(Notification notification) {
        ChannelSender sender = senderMap.get(notification.getChannel());
        if (sender == null) {
            throw new IllegalStateException("No sender registered for channel: " + notification.getChannel());
        }
        sender.send(notification);
    }
}
