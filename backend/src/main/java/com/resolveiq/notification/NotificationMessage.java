package com.resolveiq.notification;

import com.resolveiq.user.domain.User;

/**
 * Channel-independent content of one notification for one recipient. Each channel decides how to
 * render it (an e-mail body, a 160-character SMS, an in-app card).
 */
public record NotificationMessage(User recipient, String subject, String body, String incidentNumber,
                                  String eventType, boolean urgent) {

    /** SMS-sized text: subject only, truncated to 160 characters. */
    public String shortText() {
        String text = "[ResolveIQ] " + subject;
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
    }
}
