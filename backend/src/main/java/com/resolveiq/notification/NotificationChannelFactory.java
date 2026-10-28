package com.resolveiq.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * <b>Factory pattern</b>: hands out the {@link NotificationChannel} implementation for a
 * {@link ChannelType}, honouring which channels are switched on in configuration. Callers ask for
 * "EMAIL" and never construct or look up channel classes themselves.
 */
@Component
public class NotificationChannelFactory {

    private final Map<ChannelType, NotificationChannel> channels = new EnumMap<>(ChannelType.class);
    private final Set<ChannelType> enabled;

    public NotificationChannelFactory(List<NotificationChannel> available,
                                      @Value("${itsm.notifications.enabled-channels:IN_APP,EMAIL}") String enabledList) {
        available.forEach(c -> channels.put(c.type(), c));
        Set<ChannelType> on = EnumSet.noneOf(ChannelType.class);
        Arrays.stream(enabledList.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(ChannelType::valueOf).forEach(on::add);
        this.enabled = on;
    }

    public Optional<NotificationChannel> channelFor(ChannelType type) {
        return enabled.contains(type) ? Optional.ofNullable(channels.get(type)) : Optional.empty();
    }

    public List<NotificationChannel> channelsFor(Collection<ChannelType> types) {
        return types.stream().map(this::channelFor).flatMap(Optional::stream).toList();
    }

    public Set<ChannelType> enabledChannels() {
        return EnumSet.copyOf(enabled.isEmpty() ? EnumSet.of(ChannelType.IN_APP) : enabled);
    }
}
