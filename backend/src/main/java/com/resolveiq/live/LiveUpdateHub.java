package com.resolveiq.live;

import com.resolveiq.user.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Real-time push to browsers using Server-Sent Events. Each logged-in browser tab holds one
 * long-lived connection; the hub keeps them in a thread-safe map keyed by user id so notifications
 * and dashboard updates appear instantly without polling.
 */
@Component
public class LiveUpdateHub {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdateHub.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private record Connection(Long userId, Role role, SseEmitter emitter) {
    }

    private final Map<Long, Set<Connection>> connections = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId, Role role) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Connection connection = new Connection(userId, role, emitter);
        connections.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>()).add(connection);
        Runnable remove = () -> {
            Set<Connection> set = connections.get(userId);
            if (set != null) {
                set.remove(connection);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(connection, "connected", Map.of("userId", userId));
        return emitter;
    }

    public void sendToUser(Long userId, String eventName, Object payload) {
        if (userId == null) {
            return;
        }
        Set<Connection> set = connections.get(userId);
        if (set != null) {
            set.forEach(c -> send(c, eventName, payload));
        }
    }

    /** Sends to every connected IT staff member (technicians, leads, admins, managers). */
    public void broadcastToStaff(String eventName, Object payload) {
        connections.values().forEach(set -> set.stream()
                .filter(c -> c.role() != Role.EMPLOYEE)
                .forEach(c -> send(c, eventName, payload)));
    }

    public int connectionCount() {
        return connections.values().stream().mapToInt(Set::size).sum();
    }

    private void send(Connection c, String eventName, Object payload) {
        try {
            c.emitter().send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException | IllegalStateException ex) {
            log.debug("Dropping closed SSE connection for user {}", c.userId());
            Set<Connection> set = connections.get(c.userId());
            if (set != null) {
                set.remove(c);
            }
        }
    }
}
