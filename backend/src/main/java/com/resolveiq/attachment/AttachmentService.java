package com.resolveiq.attachment;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.common.exception.AttachmentRejectedException;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.UnauthorizedOperationException;
import com.resolveiq.incident.IncidentAccessPolicy;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.common.exception.IncidentNotFoundException;
import com.resolveiq.live.LiveUpdateHub;
import com.resolveiq.security.crypto.Tokens;
import com.resolveiq.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Upload, list, download and delete incident attachments.
 *
 * <p>Uploads are treated as hostile until proven otherwise:</p>
 * <ol>
 *   <li>size limit (5 MB) and a per-incident count limit (10);</li>
 *   <li>the real type is detected from the file's bytes and must be on an allow-list, and must match the
 *       file name's extension (a {@code .png} that is really HTML is refused);</li>
 *   <li>a malware scan;</li>
 *   <li>the file name is cleaned of path separators and control characters.</li>
 * </ol>
 * Downloads are served with the detected type, {@code nosniff} and a sandboxing CSP (see the controller).
 */
@Service
@Transactional
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_PER_INCIDENT = 10;

    /** A file and its metadata, ready to send. */
    public record Download(Attachment attachment, byte[] data) {
    }

    private final AttachmentRepository attachments;
    private final AttachmentContentRepository contents;
    private final IncidentRepository incidents;
    private final IncidentAccessPolicy access;
    private final MalwareScanner scanner;
    private final AuditService audit;
    private final LiveUpdateHub hub;

    public AttachmentService(AttachmentRepository attachments, AttachmentContentRepository contents,
                             IncidentRepository incidents, IncidentAccessPolicy access, MalwareScanner scanner,
                             AuditService audit, LiveUpdateHub hub) {
        this.attachments = attachments;
        this.contents = contents;
        this.incidents = incidents;
        this.access = access;
        this.scanner = scanner;
        this.audit = audit;
        this.hub = hub;
    }

    public Attachment upload(String number, String originalName, byte[] data, User uploader) {
        Incident incident = load(number);
        if (uploader != null) {
            access.requireView(uploader, incident);
        }
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new AttachmentRejectedException(number + " is closed; files can no longer be added");
        }
        if (data == null || data.length == 0) {
            throw new AttachmentRejectedException("The file is empty");
        }
        if (data.length > MAX_BYTES) {
            throw new AttachmentRejectedException("Files can be at most 5 MB");
        }
        if (attachments.countByIncident(incident.getId()) >= MAX_PER_INCIDENT) {
            throw new AttachmentRejectedException("An incident can have at most " + MAX_PER_INCIDENT + " files");
        }
        String name = cleanFileName(originalName);
        FileTypeDetector.FileType type = FileTypeDetector.detect(data, name).orElseThrow(() ->
                new AttachmentRejectedException("This file type is not allowed. Attach images (PNG, JPG, GIF, WebP), "
                        + "PDF, text or log files, or Word/Excel/PowerPoint documents."));
        Optional<String> threat = scanner.scan(data);
        if (threat.isPresent()) {
            log.warn("Upload to {} rejected: {} detected in {}", number, threat.get(), name);
            audit.recordIncidentAction(uploader, AuditAction.ATTACHMENT_REMOVED, incident.getId(), number,
                    "Upload of \"" + name + "\" blocked: malware signature " + threat.get());
            throw new AttachmentRejectedException("The file was blocked by the virus scanner (" + threat.get() + ")");
        }
        String sha = HexFormat.of().formatHex(Tokens.sha256Bytes(data));
        Attachment saved = attachments.save(new Attachment(incident, uploader, name, type.mime(), data.length, sha));
        contents.save(new AttachmentContent(saved.getId(), data));
        audit.recordIncidentAction(uploader, AuditAction.ATTACHMENT_ADDED, incident.getId(), number,
                "Attached \"" + name + "\" (" + human(data.length) + ")");
        hub.incidentTouched(number, incident.getReporter().getId(), "AttachmentAdded");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Attachment> list(String number, User viewer) {
        Incident incident = load(number);
        access.requireView(viewer, incident);
        return attachments.findByIncident(incident.getId());
    }

    @Transactional(readOnly = true)
    public Download download(Long id, User viewer) {
        Attachment a = attachments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Attachment", id));
        access.requireView(viewer, a.getIncident());
        byte[] data = contents.findById(id).map(AttachmentContent::getData)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", id));
        return new Download(a, data);
    }

    public void delete(Long id, User actor) {
        Attachment a = attachments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Attachment", id));
        Incident incident = a.getIncident();
        access.requireView(actor, incident);
        boolean uploader = a.getUploadedBy() != null && a.getUploadedBy().getId().equals(actor.getId());
        if (!uploader && !Actor.of(actor).isManagement()) {
            throw new UnauthorizedOperationException("Only the person who added a file, or a supervisor, can remove it");
        }
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new UnauthorizedOperationException(incident.getNumber() + " is closed; its files are part of the record");
        }
        contents.deleteById(id);
        attachments.delete(a);
        audit.recordIncidentAction(actor, AuditAction.ATTACHMENT_REMOVED, incident.getId(), incident.getNumber(),
                "Removed \"" + a.getFileName() + "\"");
        hub.incidentTouched(incident.getNumber(), incident.getReporter().getId(), "AttachmentRemoved");
    }

    private Incident load(String number) {
        return incidents.findByNumberIgnoreCase(number == null ? "" : number.trim())
                .orElseThrow(() -> new IncidentNotFoundException(number));
    }

    /** Keeps only the last path segment and safe characters; never trust a client-supplied name. */
    static String cleanFileName(String original) {
        String name = original == null ? "file" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]", "").replaceAll("\\s+", " ").trim();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isBlank()) {
            name = "file";
        }
        if (name.length() > 120) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 6 ? name.substring(dot) : "";
            name = name.substring(0, 120 - ext.length()) + ext;
        }
        return name;
    }

    static String human(long bytes) {
        return bytes < 1024 ? bytes + " B" : bytes < 1024 * 1024 ? (bytes / 1024) + " KB"
                : String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
