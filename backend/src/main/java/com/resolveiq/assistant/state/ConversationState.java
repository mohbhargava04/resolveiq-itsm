package com.resolveiq.assistant.state;

import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.ConversationStep;
import com.resolveiq.assistant.Prompt;
import com.resolveiq.assistant.StepResult;
import com.resolveiq.assistant.UserInput;

/**
 * <b>State pattern.</b> One object per step of a "Report with assistant" conversation. The
 * {@link ChatSession} is the context: it stores which step it is in, and the matching state object
 * decides what to ask and what an answer means. Adding a step to the conversation means adding one
 * class and one {@link ConversationStep} constant - no existing state changes.
 *
 * <p>States are stateless singletons (Spring beans); everything about one conversation lives in
 * the session, so they are safe to share between all users.</p>
 */
public interface ConversationState {

    ConversationStep step();

    /**
     * Whether this step still has something to ask in this conversation. This is how the assistant
     * "asks only for what is missing": the flow visits the first step that applies.
     */
    boolean applies(ChatSession session);

    /** What the assistant says and offers on entering (or repeating) this step. */
    Prompt prompt(ChatSession session);

    /** Reads the person's answer, updates the session, and says what should happen next. */
    StepResult handle(ChatSession session, UserInput input);
}
