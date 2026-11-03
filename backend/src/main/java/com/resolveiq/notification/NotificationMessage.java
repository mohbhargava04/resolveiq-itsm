package com.resolveiq.notification;

import com.resolveiq.user.domain.User;

/**
 * Channel-independent content of one notification for one recipient. Each channel decides how to
 * render it (an e-mail body, a 160-character SMS, an in-app card).
 */
public record NotificationMessage(User recipient, String subject, String body, String incidentNumber,
                                  String eventType, boolean urgent, String loggedBody) {

    public NotificationMessage(User recipient, String subject, String body, String incidentNumber,
                               String eventType, boolean urgent) {
        this(recipient, subject, body, incidentNumber, eventType, urgent, null);
    }

    /**
     * What the delivery log keeps. Messages containing a secret (a password-reset link) pass a
     * redacted version here, so the secret only ever exists in the recipient's inbox.
     */
    public String bodyForLog() {
        return loggedBody == null ? body : loggedBody;
    }

    /** SMS-sized text: subject only, truncated to 160 characters. */
    public String shortText() {
        String text = "[ResolveIQ] " + subject;
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
    }
}
