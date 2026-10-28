package com.resolveiq.common.exception;

import java.util.Map;

/**
 * Raised by the incident State objects when an operation is not permitted in the current lifecycle
 * state, e.g. closing an incident that is still NEW.
 */
public class InvalidStateTransitionException extends ItsmException {

    private final String currentState;
    private final String attemptedAction;

    public InvalidStateTransitionException(String currentState, String attemptedAction) {
        super(ErrorCode.INVALID_STATE_TRANSITION,
                "Cannot " + attemptedAction.toLowerCase().replace('_', ' ')
                        + " an incident that is " + currentState);
        this.currentState = currentState;
        this.attemptedAction = attemptedAction;
    }

    public String getCurrentState() {
        return currentState;
    }

    public String getAttemptedAction() {
        return attemptedAction;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("currentState", currentState, "attemptedAction", attemptedAction);
    }
}
