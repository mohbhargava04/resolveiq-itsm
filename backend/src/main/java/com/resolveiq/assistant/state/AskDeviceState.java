package com.resolveiq.assistant.state;

import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.ConversationStep;
import com.resolveiq.assistant.Prompt;
import com.resolveiq.assistant.StepResult;
import com.resolveiq.assistant.UserInput;

import com.resolveiq.assistant.DraftInsight;
import com.resolveiq.assistant.IncidentDraft;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * "Which device?" - asked only when it helps the technician (hardware and network problems) and
 * the description did not already name one. Optional: the person can skip it.
 */
@Component
class AskDeviceState extends AbstractConversationState {

    static final String SKIP = "SKIP";
    /** Categories where the first thing a technician asks is "on which device?". */
    static final Set<String> DEVICE_CATEGORIES = Set.of("HARDWARE", "NETWORK");

    private final DraftInsight insight;

    AskDeviceState(DraftInsight insight) {
        this.insight = insight;
    }

    @Override
    public ConversationStep step() {
        return ConversationStep.ASK_DEVICE;
    }

    @Override
    public boolean applies(ChatSession session) {
        IncidentDraft draft = session.getDraft();
        return !draft.isDeviceAsked() && draft.getDevice() == null
                && insight.category(draft).map(c -> DEVICE_CATEGORIES.contains(c.getCode())).orElse(false);
    }

    @Override
    public Prompt prompt(ChatSession session) {
        return Prompt.ask("Which device is this on? It helps the technician start faster - or skip this.")
                .option("Laptop", "Laptop").option("Desktop", "Desktop").option("Phone", "Phone")
                .option(SKIP, "Skip")
                .freeText("Or type the device…")
                .build();
    }

    @Override
    protected StepResult answer(ChatSession session, UserInput input) {
        if (input.chose(SKIP) || input.text().equalsIgnoreCase("skip")) {
            session.getDraft().skipDevice();
        } else {
            session.getDraft().noteDevice(input.choice() != null ? input.choice() : input.text());
        }
        return StepResult.next();
    }
}
