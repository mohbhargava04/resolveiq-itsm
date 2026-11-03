package com.resolveiq.notification.inbound;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the useful parts out of an e-mail: the incident reference in the subject ({@code [INC-1042]})
 * and the new text of a reply, without the quoted original message and signature underneath.
 */
public final class EmailReplyParser {

    private static final Pattern TAG = Pattern.compile("\\[(INC-\\d{1,9})]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDRESS = Pattern.compile("<?([A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})>?");
    private static final Pattern QUOTE_HEADER = Pattern.compile(
            "^(On .+wrote:|-{2,}\\s*Original Message\\s*-{2,}|From: .+|Sent from my .+|-- ?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPLY_PREFIX = Pattern.compile("^((re|fw|fwd|aw)\\s*:\\s*)+", Pattern.CASE_INSENSITIVE);

    private EmailReplyParser() {
    }

    public static Optional<String> incidentNumber(String subject) {
        if (subject == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(subject);
        return m.find() ? Optional.of(m.group(1).toUpperCase()) : Optional.empty();
    }

    /** {@code "Ava Sharma <ava@x.io>"} → {@code ava@x.io} */
    public static Optional<String> address(String from) {
        if (from == null) {
            return Optional.empty();
        }
        Matcher m = ADDRESS.matcher(from);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return Optional.ofNullable(last).map(String::toLowerCase);
    }

    /** The reply text above the quoted message. */
    public static String newText(String body) {
        if (body == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String raw : body.replace("\r", "").split("\n")) {
            String line = raw.stripTrailing();
            if (QUOTE_HEADER.matcher(line.trim()).matches()) {
                break;
            }
            if (line.trim().startsWith(">")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString().strip();
    }

    /** Subject without "Re:"/"Fwd:" prefixes and the incident tag. */
    public static String cleanSubject(String subject) {
        if (subject == null) {
            return "";
        }
        return REPLY_PREFIX.matcher(TAG.matcher(subject).replaceAll("").trim()).replaceAll("").trim();
    }
}
