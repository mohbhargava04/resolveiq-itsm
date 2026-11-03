package com.resolveiq.live;

import com.resolveiq.security.crypto.Tokens;
import com.resolveiq.user.domain.Role;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time tickets for the live-update stream.
 *
 * <p>The browser's {@code EventSource} cannot send an {@code Authorization} header, so something has
 * to go in the URL - and URLs end up in server and proxy logs. Instead of the access token, the
 * browser first asks for a ticket (an authenticated POST) and opens the stream with that. A ticket is
 * random, works <b>once</b>, and expires after 60 seconds, so a ticket found in a log is useless.</p>
 */
@Component
public class LiveTicketService {

    static final Duration VALIDITY = Duration.ofSeconds(60);

    public record Holder(Long userId, Role role) {
    }

    private record Ticket(Holder holder, Instant expiresAt) {
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Clock clock;

    public LiveTicketService(Clock clock) {
        this.clock = clock;
    }

    public String issue(Long userId, Role role) {
        Instant now = clock.instant();
        tickets.values().removeIf(t -> t.expiresAt().isBefore(now));
        String ticket = Tokens.random(24);
        tickets.put(ticket, new Ticket(new Holder(userId, role), now.plus(VALIDITY)));
        return ticket;
    }

    /** Consumes the ticket: a second use, or use after expiry, returns empty. */
    public Optional<Holder> redeem(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Ticket t = tickets.remove(ticket);
        if (t == null || t.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(t.holder());
    }
}
