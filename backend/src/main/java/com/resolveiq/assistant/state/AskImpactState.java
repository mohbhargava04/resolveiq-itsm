package com.resolveiq.assistant.state;

import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.ConversationStep;
import com.resolveiq.assistant.Prompt;
import com.resolveiq.assistant.StepResult;
import com.resolveiq.assistant.UserInput;

import com.resolveiq.assistant.AnswerInterpreter;
import com.resolveiq.incident.domain.Impact;
import org.springframework.stereotype.Component;

/** "Who is affected?" - skipped when the description already said. */
@Component
class AskImpactState extends AbstractConversationState {

    @Override
    public ConversationStep step() {
        return ConversationStep.ASK_IMPACT;
    }

    @Override
    public boolean applies(ChatSession session) {
        return session.getDraft().getImpact() == null;
    }

    @Override
    public Prompt prompt(ChatSession session) {
        return Prompt.ask("Who is affected?")
                .option(Impact.LOW.name(), "Just me", Impact.LOW.description())
                .option(Impact.MEDIUM.name(), "My team or department", Impact.MEDIUM.description())
                .option(Impact.HIGH.name(), "Several departments", Impact.HIGH.description())
                .option(Impact.CRITICAL.name(), "The whole organisation or customers", Impact.CRITICAL.description())
                .build();
    }

    @Override
    protected StepResult answer(ChatSession session, UserInput input) {
        return AnswerInterpreter.impactAnswer(input).map(impact -> {
            session.getDraft().rateImpact(impact);
            return StepResult.next();
        }).orElseGet(() -> StepResult.notUnderstood(PICK_ONE));
    }
}
