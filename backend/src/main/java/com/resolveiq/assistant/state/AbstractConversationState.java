package com.resolveiq.assistant.state;

import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.StepResult;
import com.resolveiq.assistant.UserInput;

/**
 * <b>Template method</b>: the checks every step shares (an empty turn is never an answer) live here;
 * subclasses only implement {@link #answer}.
 */
abstract class AbstractConversationState implements ConversationState {

    static final String PICK_ONE = "Sorry, I didn't catch that. Please choose one of the options below.";

    @Override
    public final StepResult handle(ChatSession session, UserInput input) {
        if (input == null || input.isEmpty()) {
            return StepResult.notUnderstood(emptyAnswerMessage());
        }
        return answer(session, input);
    }

    protected abstract StepResult answer(ChatSession session, UserInput input);

    protected String emptyAnswerMessage() {
        return PICK_ONE;
    }
}
