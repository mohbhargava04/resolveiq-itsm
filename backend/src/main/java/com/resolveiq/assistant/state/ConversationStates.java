package com.resolveiq.assistant.state;

import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.ConversationStep;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of the conversation's state objects and the rule for moving between them.
 *
 * <p>Spring injects every {@link ConversationState} bean, so a new step is picked up just by
 * existing (open/closed principle). The registry refuses to start if a step has no state object or
 * two objects claim the same step.</p>
 *
 * <p><b>Flow</b>: the next step is the first one, in {@link ConversationStep} order, that still
 * {@linkplain ConversationState#applies applies}. States therefore never name each other; they
 * only record what they learnt, and the order of the conversation is defined in one place.</p>
 */
@Component
public class ConversationStates {

    private final Map<ConversationStep, ConversationState> states = new EnumMap<>(ConversationStep.class);

    public ConversationStates(List<ConversationState> beans) {
        for (ConversationState state : beans) {
            if (states.put(state.step(), state) != null) {
                throw new IllegalStateException("Two conversation states registered for " + state.step());
            }
        }
        for (ConversationStep step : ConversationStep.values()) {
            if (step != ConversationStep.FINISHED && !states.containsKey(step)) {
                throw new IllegalStateException("No conversation state registered for " + step);
            }
        }
    }

    public ConversationState of(ConversationStep step) {
        ConversationState state = states.get(step);
        if (state == null) {
            throw new IllegalStateException("No conversation state for " + step);
        }
        return state;
    }

    /** The step to ask next: the first one that still has something to ask. */
    public ConversationState next(ChatSession session) {
        return states.values().stream().filter(s -> s.applies(session)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No conversation step applies"));
    }
}
