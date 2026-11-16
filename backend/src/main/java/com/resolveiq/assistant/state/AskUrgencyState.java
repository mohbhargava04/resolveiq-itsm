package com.resolveiq.assistant.state;

import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.ConversationStep;
import com.resolveiq.assistant.Prompt;
import com.resolveiq.assistant.StepResult;
import com.resolveiq.assistant.UserInput;

import com.resolveiq.assistant.AnswerInterpreter;
import com.resolveiq.incident.domain.Urgency;
import org.springframework.stereotype.Component;

/** "How urgent is it?" - skipped when the description already said. */
@Component
class AskUrgencyState extends AbstractConversationState {

    @Override
    public ConversationStep step() {
        return ConversationStep.ASK_URGENCY;
    }

    @Override
    public boolean applies(ChatSession session) {
        return session.getDraft().getUrgency() == null;
    }

    @Override
    public Prompt prompt(ChatSession session) {
        return Prompt.ask("How urgently do you need it fixed?")
                .option(Urgency.LOW.name(), "It can wait", Urgency.LOW.description())
                .option(Urgency.MEDIUM.name(), "Today", Urgency.MEDIUM.description())
                .option(Urgency.HIGH.name(), "Within hours - my work is blocked", Urgency.HIGH.description())
                .option(Urgency.CRITICAL.name(), "Immediately", Urgency.CRITICAL.description())
                .build();
    }

    @Override
    protected StepResult answer(ChatSession session, UserInput input) {
        return AnswerInterpreter.urgencyAnswer(input).map(urgency -> {
            session.getDraft().rateUrgency(urgency);
            return StepResult.next();
        }).orElseGet(() -> StepResult.notUnderstood(PICK_ONE));
    }
}
