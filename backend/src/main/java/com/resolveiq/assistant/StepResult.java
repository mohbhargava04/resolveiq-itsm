package com.resolveiq.assistant;

/**
 * What a conversation state decided after reading an answer.
 * <ul>
 *   <li>{@link Kind#CONTINUE} - the answer was used; the flow picks the next thing to ask
 *       (which may be this same step again, e.g. after "something else").</li>
 *   <li>{@link Kind#NOT_UNDERSTOOD} - the answer made no sense here; stay and show the options again.</li>
 *   <li>{@link Kind#FINISH} - the conversation is over (the state has already ended the session).</li>
 * </ul>
 */
public record StepResult(Kind kind, String message) {

    public enum Kind { CONTINUE, NOT_UNDERSTOOD, FINISH }

    public static StepResult next() {
        return new StepResult(Kind.CONTINUE, null);
    }

    public static StepResult next(String acknowledgement) {
        return new StepResult(Kind.CONTINUE, acknowledgement);
    }

    public static StepResult notUnderstood(String message) {
        return new StepResult(Kind.NOT_UNDERSTOOD, message);
    }

    public static StepResult finished(String message) {
        return new StepResult(Kind.FINISH, message);
    }
}
