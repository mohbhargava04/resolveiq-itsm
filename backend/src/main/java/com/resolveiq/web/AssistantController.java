package com.resolveiq.web;

import com.resolveiq.assistant.AssistantReply;
import com.resolveiq.assistant.ChatSession;
import com.resolveiq.assistant.ChatbotService;
import com.resolveiq.assistant.UserInput;
import com.resolveiq.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * "Report with assistant": a conversation that ends in a self-help fix, joining a major incident,
 * or an incident created through the normal pipeline. Any signed-in person can use it; a
 * conversation can only be read or continued by the person who started it.
 */
@RestController
@RequestMapping("/api/assistant/sessions")
@Tag(name = "Assistant (chatbot reporting)")
public class AssistantController {

    /** One turn from the person: typed (or dictated) text, the value of a tapped option, or both. */
    public record MessageRequest(@Size(max = 4200, message = "Please keep it under 4,000 characters") String text,
                                 @Size(max = 60) String choice) {
    }

    public record TranscriptLine(String sender, String text, Instant sentAt) {
    }

    public record Transcript(Long id, String step, String outcome, String incidentNumber, Instant startedAt,
                             Instant finishedAt, List<TranscriptLine> messages) {
    }

    private final ChatbotService chatbot;
    private final CurrentUserService currentUser;

    public AssistantController(ChatbotService chatbot, CurrentUserService currentUser) {
        this.chatbot = chatbot;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a conversation; returns the assistant's greeting and first question")
    public AssistantReply start() {
        return chatbot.start(currentUser.require());
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Send an answer (text and/or the chosen option); returns the assistant's next turn")
    public AssistantReply reply(@PathVariable Long id, @Valid @RequestBody MessageRequest r) {
        return chatbot.reply(id, currentUser.require(), new UserInput(r.text(), r.choice()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "The conversation transcript and how it ended (owner only)")
    public Transcript transcript(@PathVariable Long id) {
        ChatSession s = chatbot.get(id, currentUser.require());
        return new Transcript(s.getId(), s.getStep().name(), s.getOutcome().name(), s.getIncidentNumber(),
                s.getCreatedAt(), s.getFinishedAt(), s.getMessages().stream()
                .map(m -> new TranscriptLine(m.getSender().name(), m.getText(), m.getSentAt())).toList());
    }
}
