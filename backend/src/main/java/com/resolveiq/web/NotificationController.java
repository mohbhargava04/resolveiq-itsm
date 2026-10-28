package com.resolveiq.web;

import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.live.LiveUpdateHub;
import com.resolveiq.notification.ChannelType;
import com.resolveiq.notification.Notification;
import com.resolveiq.notification.NotificationRepository;
import com.resolveiq.security.CurrentUserService;
import com.resolveiq.user.domain.User;
import com.resolveiq.web.dto.CommonDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.Map;

/** The signed-in user's in-app notification inbox and the real-time event stream. */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    public record NotificationView(Long id, String subject, String message, String incidentNumber, String eventType,
                                   boolean urgent, boolean read, Instant createdAt) {

        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getSubject(), n.getMessage(), n.getIncidentNumber(),
                    n.getEventType(), n.isUrgent(), n.isRead(), n.getCreatedAt());
        }
    }

    private final NotificationRepository notifications;
    private final CurrentUserService currentUser;
    private final LiveUpdateHub hub;

    public NotificationController(NotificationRepository notifications, CurrentUserService currentUser,
                                  LiveUpdateHub hub) {
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.hub = hub;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<NotificationView> inbox(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        User me = currentUser.require();
        return PageResponse.of(notifications.findByRecipientIdAndChannelOrderByCreatedAtDesc(me.getId(),
                ChannelType.IN_APP, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100))),
                NotificationView::of);
    }

    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public Map<String, Long> unread() {
        User me = currentUser.require();
        return Map.of("unread", notifications.countByRecipientIdAndChannelAndReadFalse(me.getId(), ChannelType.IN_APP));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public NotificationView markRead(@PathVariable Long id) {
        User me = currentUser.require();
        Notification n = notifications.findById(id)
                .filter(x -> x.getRecipient().getId().equals(me.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        n.markRead();
        return NotificationView.of(n);
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Integer> markAllRead() {
        User me = currentUser.require();
        return Map.of("updated", notifications.markAllRead(me.getId()));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Server-sent events: live notifications and incident updates (pass ?access_token=)")
    public SseEmitter stream() {
        User me = currentUser.require();
        return hub.connect(me.getId(), me.getRole());
    }
}
