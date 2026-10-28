package com.resolveiq.web.dto;

import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.IncidentAction;
import com.resolveiq.incident.domain.NoteType;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.incident.domain.Urgency;
import com.resolveiq.web.dto.CommonDtos.PersonRef;
import com.resolveiq.web.dto.CommonDtos.Ref;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Request and response bodies of the incident API. */
public final class IncidentDtos {

    private IncidentDtos() {
    }

    // ---- requests ------------------------------------------------------------------------------

    public record ReportIncidentRequest(
            @NotBlank(message = "Title is required") @Size(max = 160, message = "Title must be at most 160 characters") String title,
            @NotBlank(message = "Description is required") @Size(max = 4000, message = "Description must be at most 4000 characters") String description,
            @NotBlank(message = "Affected service is required") String serviceCode,
            String categoryCode,
            @NotNull(message = "Impact is required") Impact impact,
            @NotNull(message = "Urgency is required") Urgency urgency,
            @Min(value = 1, message = "At least one user must be affected") @Max(value = 1_000_000, message = "Too many affected users") Integer affectedUsers,
            boolean acknowledgeDuplicates) {
    }

    public record UpdateIncidentRequest(@Size(max = 160) String title, @Size(max = 4000) String description,
                                        Impact impact, Urgency urgency,
                                        @Min(1) @Max(1_000_000) Integer affectedUsers) {
    }

    public record AssignRequest(Long technicianId, String strategy) {
    }

    public record TextRequest(@NotBlank(message = "Text is required") @Size(max = 4000) String text) {
    }

    public record OptionalTextRequest(@Size(max = 1000) String text) {
    }

    public record ResolveRequest(@NotBlank(message = "Resolution details are required") @Size(max = 2000) String summary,
                                 @Size(max = 1000) String rootCause) {
    }

    public record NoteRequest(@NotNull NoteType type, @NotBlank @Size(max = 4000) String content, boolean internal) {
    }

    public record FeedbackRequest(@NotNull @Min(value = 1, message = "Rating must be 1-5") @Max(value = 5, message = "Rating must be 1-5") Integer rating,
                                  @Size(max = 1000) String comment) {
    }

    public record ReprioritiseRequest(Priority priority, @Size(max = 500) String reason) {
    }

    public record DuplicateRequest(@NotBlank String parentNumber) {
    }

    // ---- responses -----------------------------------------------------------------------------

    public record IncidentSummary(String number, String title, String status, String priority, String priorityLabel,
                                  Ref category, Ref service, PersonRef reporter, PersonRef assignee,
                                  Instant createdAt, Instant updatedAt, String slaStatus, Instant responseDueAt,
                                  Instant resolutionDueAt, Long minutesRemaining, String escalationLevel,
                                  String suspectedDuplicateOf, boolean autoClassified) {
    }

    public record SlaView(String policyName, Integer responseMinutes, Integer resolutionMinutes, Instant startedAt,
                          Instant responseDueAt, Instant resolutionDueAt, Instant respondedAt, Instant resolvedAt,
                          boolean responseBreached, boolean resolutionBreached, boolean paused, String status,
                          int elapsedPercent, Long minutesRemaining, Long responseMinutesTaken,
                          Long resolutionMinutesTaken) {
    }

    public record EscalationView(String level, String label, Instant escalatedAt, PersonRef escalatedTo) {
    }

    public record ResolutionView(String summary, String rootCause, PersonRef resolvedBy, Instant resolvedAt) {
    }

    public record FeedbackView(Integer rating, String comment, Instant submittedAt) {
    }

    public record DuplicateView(String number, String title, Double score, boolean confirmed) {
    }

    public record NoteView(Long id, String type, boolean internal, String author, String content, Instant createdAt) {
    }

    public record IncidentDetail(String number, String title, String description, String status, String statusLabel,
                                 String priority, String priorityLabel, Double priorityScore, String priorityRationale,
                                 String priorityStrategy, Ref category, boolean autoClassified,
                                 Double classificationConfidence, Ref service, String serviceCriticality,
                                 String impact, String urgency, int affectedUsers, PersonRef reporter,
                                 PersonRef assignee, Instant assignedAt, String assignmentStrategy, SlaView sla,
                                 EscalationView escalation, ResolutionView resolution, FeedbackView feedback,
                                 DuplicateView duplicateOf, Instant createdAt, Instant updatedAt, Instant closedAt,
                                 String closureNote, List<NoteView> notes, Set<IncidentAction> availableActions,
                                 Set<String> nextStatuses) {
    }

    public record SimilarIncidentView(String number, String title, String status, String service, double score,
                                      double textSimilarity, boolean sameService) {
    }

    public record ReportResponse(IncidentDetail incident, List<SimilarIncidentView> similarIncidents,
                                 Object categorySuggestion, String assignmentMessage) {
    }

    public record AuditEntry(Long id, Instant occurredAt, String actor, String actorRole, String action,
                             String incidentNumber, String previousState, String newState, String description) {
    }
}
