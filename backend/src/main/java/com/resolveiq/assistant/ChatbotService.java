package com.resolveiq.assistant;

import com.resolveiq.assistant.state.ConversationState;
import com.resolveiq.assistant.state.ConversationStates;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.UnauthorizedOperationException;
import com.resolveiq.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * <b>Innovation challenge - chatbot-based incident reporting.</b> Runs "Report with assistant"
 * conversations: it loads the session, hands the answer to the current {@link ConversationState},
 * then asks the next thing that is still missing.
 *
 * <p>The assistant is deterministic (rules plus the existing Naive Bayes classifier, TF-IDF
 * similarity and knowledge base); it needs no external AI service, so every path can be unit-tested
 * and it behaves the same in a demo as in the test suite. It owns no business rule of its own:
 * incidents are created by {@code IncidentService}, deflections recorded by {@code KnowledgeService}.</p>
 */
@Service
@Transactional
public class ChatbotService {

    public static final String CANCEL = "CANCEL";
    public static final String RESTART = "RESTART";
    private static final Set<String> CANCEL_WORDS = Set.of("cancel", "stop", "quit", "never mind", "nevermind", "exit");
    private static final Set<String> RESTART_WORDS = Set.of("start over", "restart", "start again", "reset");

    private final ChatSessionRepository sessions;
    private final ConversationStates states;
    private final Clock clock;

    public ChatbotService(ChatSessionRepository sessions, ConversationStates states, Clock clock) {
        this.sessions = sessions;
        this.states = states;
        this.clock = clock;
    }

    /** Opens a conversation and asks the first question. */
    public AssistantReply start(User user) {
        ChatSession session = sessions.save(new ChatSession(user));
        String firstName = user.getFullName().split("\\s+")[0];
        List<String> say = new ArrayList<>();
        say.add("Hi " + firstName + ", I'm the ResolveIQ assistant. I can help you fix a problem yourself, "
                + "or report it to the IT team for you.");
        return ask(session, say);
    }

    /** Handles one answer and returns the assistant's next turn. */
    public AssistantReply reply(Long sessionId, User user, UserInput input) {
        ChatSession session = load(sessionId, user);
        Instant now = clock.instant();
        if (session.isFinished()) {
            return AssistantReply.of(session, List.of("This conversation has ended. Start a new one if you need "
                    + "anything else."), null);
        }
        session.say(MessageSender.USER, input.display(), now);
        List<String> say = new ArrayList<>();

        // two commands work at every step
        if (isCommand(input, CANCEL, CANCEL_WORDS)) {
            session.cancel(now);
            return finish(session, say, "OK, I've cancelled this. Nothing was created.");
        }
        if (isCommand(input, RESTART, RESTART_WORDS)) {
            session.restart();
            say.add("OK, let's start again.");
            return ask(session, say);
        }

        ConversationState state = states.of(session.getStep());
        StepResult result = state.handle(session, input);
        switch (result.kind()) {
            case FINISH -> {
                return finish(session, say, result.message());
            }
            case NOT_UNDERSTOOD -> {
                // stay on the step: repeat only the options, not the whole question
                say.add(result.message());
                say.forEach(line -> session.say(MessageSender.ASSISTANT, line, now));
                return AssistantReply.of(session, say, state.prompt(session));
            }
            default -> {
                if (result.message() != null) {
                    say.add(result.message());
                }
                return ask(session, say);
            }
        }
    }

    /** The conversation as it stands, for its owner only. */
    @Transactional(readOnly = true)
    public ChatSession get(Long sessionId, User user) {
        return load(sessionId, user);
    }

    // ---- helpers -------------------------------------------------------------------------------

    private AssistantReply ask(ChatSession session, List<String> say) {
        ConversationState next = states.next(session);
        session.moveTo(next.step());
        Prompt prompt = next.prompt(session);
        say.add(prompt.text());
        Instant now = clock.instant();
        say.forEach(line -> session.say(MessageSender.ASSISTANT, line, now));
        return AssistantReply.of(session, say, prompt);
    }

    private AssistantReply finish(ChatSession session, List<String> say, String closing) {
        say.add(closing);
        Instant now = clock.instant();
        say.forEach(line -> session.say(MessageSender.ASSISTANT, line, now));
        return AssistantReply.of(session, say, null);
    }

    private ChatSession load(Long id, User user) {
        ChatSession session = sessions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", id));
        if (!session.isOwnedBy(user)) {
            throw new UnauthorizedOperationException("This conversation belongs to someone else");
        }
        return session;
    }

    private static boolean isCommand(UserInput input, String choice, Set<String> words) {
        return input.chose(choice)
                || (input.choice() == null && words.contains(input.text().toLowerCase(Locale.ROOT).replaceAll("[.!]+$", "")));
    }
}
