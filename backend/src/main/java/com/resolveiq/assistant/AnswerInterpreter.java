package com.resolveiq.assistant;

import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Urgency;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turns everyday phrases into the values the incident pipeline needs ("just me" → LOW impact,
 * "asap" → HIGH urgency). Rule-based on purpose: deterministic, explainable and testable, with no
 * external AI service. Used twice - to pick up hints from the first description (so the assistant
 * does not ask what it was already told) and to understand typed answers.
 */
public final class AnswerInterpreter {

    /** Checked in order, so "not urgent" is recognised before "urgent". */
    private static final Map<Urgency, List<String>> URGENCY_CUES = new LinkedHashMap<>();
    /** Widest first, so "everyone in my team" is not read as "everyone". */
    private static final Map<Impact, List<String>> IMPACT_CUES = new LinkedHashMap<>();

    private static final List<String> DEVICES = List.of("laptop", "desktop", "pc", "computer", "macbook", "mac",
            "phone", "mobile", "iphone", "android", "tablet", "ipad", "printer", "scanner", "monitor", "projector",
            "keyboard", "mouse", "router", "docking station", "headset", "webcam");

    private static final List<String> YES = List.of("yes", "y", "yeah", "yep", "yup", "correct", "right", "thats right",
            "that is right", "thats it", "ok", "okay", "sure", "confirm", "it is", "exactly");
    private static final List<String> NO = List.of("no", "n", "nope", "nah", "wrong", "incorrect", "not really",
            "it isnt", "it is not", "different");

    static {
        URGENCY_CUES.put(Urgency.LOW, List.of("not urgent", "no rush", "no hurry", "can wait", "whenever", "low priority",
                "workaround", "when you get time", "not blocking"));
        URGENCY_CUES.put(Urgency.CRITICAL, List.of("immediately", "right now", "emergency", "critical", "losing money",
                "revenue", "safety", "right away", "this minute"));
        URGENCY_CUES.put(Urgency.HIGH, List.of("urgent", "asap", "as soon as possible", "blocked", "blocking",
                "cant work", "cannot work", "unable to work", "deadline", "exam", "within the hour", "within hours",
                "stuck", "high"));
        URGENCY_CUES.put(Urgency.MEDIUM, List.of("today", "end of day", "eod", "this afternoon", "working day",
                "soon", "medium", "normal"));

        IMPACT_CUES.put(Impact.MEDIUM, List.of("my team", "our team", "whole team", "entire team", "everyone in my team",
                "my department", "our department", "our office", "my office", "colleagues", "few of us", "some of us",
                "my class", "our lab", "team", "department", "medium"));
        IMPACT_CUES.put(Impact.CRITICAL, List.of("everyone", "everybody", "whole organisation", "whole organization",
                "entire organisation", "entire organization", "organisation wide", "organization wide", "whole company",
                "entire company", "whole campus", "entire campus", "all users", "all students", "all staff",
                "customers", "nobody can", "no one can", "critical"));
        IMPACT_CUES.put(Impact.HIGH, List.of("several departments", "multiple departments", "several teams",
                "multiple teams", "many people", "lots of people", "whole building", "entire building", "whole floor",
                "key process", "high"));
        IMPACT_CUES.put(Impact.LOW, List.of("just me", "only me", "only i", "just myself", "myself", "my own",
                "single user", "one person", "nobody else", "no one else", "low", "me"));
    }

    private AnswerInterpreter() {
    }

    /** Lower case, apostrophes dropped, everything else that is not a letter or digit becomes a space. */
    static String normalise(String text) {
        return text == null ? "" : (" " + text.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "")
                .replaceAll("[^a-z0-9]+", " ").trim() + " ");
    }

    private static boolean mentions(String normalised, String phrase) {
        return normalised.contains(" " + phrase + " ");
    }

    private static <E> Optional<E> firstCue(Map<E, List<String>> cues, String text, boolean answerMode) {
        String n = normalise(text);
        for (Map.Entry<E, List<String>> e : cues.entrySet()) {
            for (String cue : e.getValue()) {
                // one-word cues such as "high" or "me" only count as a direct answer, never inside a description
                if (!answerMode && !cue.contains(" ") && isBareAnswerWord(cue)) {
                    continue;
                }
                if (mentions(n, cue)) {
                    return Optional.of(e.getKey());
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isBareAnswerWord(String cue) {
        return List.of("low", "medium", "high", "critical", "me", "team", "department", "normal", "soon", "myself",
                "today", "stuck", "revenue", "safety", "exam", "deadline").contains(cue);
    }

    /** A hint inside a longer description; only unambiguous phrases count. */
    public static Optional<Impact> impactHint(String description) {
        return firstCue(IMPACT_CUES, description, false);
    }

    public static Optional<Urgency> urgencyHint(String description) {
        return firstCue(URGENCY_CUES, description, false);
    }

    /** A direct answer to "who is affected?" - an option value, a level name or an everyday phrase. */
    public static Optional<Impact> impactAnswer(UserInput input) {
        return enumChoice(Impact.class, input.choice()).or(() -> enumChoice(Impact.class, input.text()))
                .or(() -> firstCue(IMPACT_CUES, input.text(), true));
    }

    public static Optional<Urgency> urgencyAnswer(UserInput input) {
        return enumChoice(Urgency.class, input.choice()).or(() -> enumChoice(Urgency.class, input.text()))
                .or(() -> firstCue(URGENCY_CUES, input.text(), true));
    }

    /** The first device named in the text ("my laptop keeps restarting" → "Laptop"). */
    public static Optional<String> device(String text) {
        String n = normalise(text);
        return DEVICES.stream().filter(d -> mentions(n, d) || mentions(n, d + "s")).findFirst()
                .map(d -> Character.toUpperCase(d.charAt(0)) + d.substring(1));
    }

    /** Yes/no, or empty if the text is neither. */
    public static Optional<Boolean> yesNo(String text) {
        String n = normalise(text).trim();
        if (YES.contains(n) || YES.stream().anyMatch(y -> n.startsWith(y + " "))) {
            return Optional.of(Boolean.TRUE);
        }
        if (NO.contains(n) || NO.stream().anyMatch(x -> n.startsWith(x + " "))) {
            return Optional.of(Boolean.FALSE);
        }
        return Optional.empty();
    }

    private static <E extends Enum<E>> Optional<E> enumChoice(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
