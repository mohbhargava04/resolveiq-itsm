package com.resolveiq.web;

import com.resolveiq.attachment.Attachment;
import com.resolveiq.attachment.AttachmentService;
import com.resolveiq.common.exception.AttachmentRejectedException;
import com.resolveiq.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@RestController
@Tag(name = "Attachments")
public class AttachmentController {

    public record AttachmentView(Long id, String fileName, String contentType, long sizeBytes, boolean image,
                                 String uploadedBy, Instant uploadedAt, boolean canDelete) {
    }

    private final AttachmentService attachments;
    private final CurrentUserService currentUser;

    public AttachmentController(AttachmentService attachments, CurrentUserService currentUser) {
        this.attachments = attachments;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/incidents/{number}/attachments")
    public List<AttachmentView> list(@PathVariable String number) {
        var me = currentUser.require();
        boolean supervisor = com.resolveiq.incident.domain.Actor.of(me).isManagement();
        return attachments.list(number, me).stream().map(a -> view(a,
                supervisor || (a.getUploadedBy() != null && a.getUploadedBy().getId().equals(me.getId())))).toList();
    }

    @PostMapping(path = "/api/incidents/{number}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attach a file (max 5 MB; images, PDF, text/logs, Office documents)")
    public AttachmentView upload(@PathVariable String number, @RequestParam("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new AttachmentRejectedException("The upload could not be read");
        }
        return view(attachments.upload(number, file.getOriginalFilename(), bytes, currentUser.require()), true);
    }

    /**
     * Served with the type detected at upload, {@code nosniff}, and {@code CSP: sandbox}, so even a
     * file that slipped past the checks cannot run script in the site's origin. Only images are shown
     * inline; everything else downloads.
     */
    @GetMapping("/api/attachments/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        AttachmentService.Download d = attachments.download(id, currentUser.require());
        Attachment a = d.attachment();
        ContentDisposition disposition = (a.isImage() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(a.getFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'; img-src 'self'")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(d.data());
    }

    @DeleteMapping("/api/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        attachments.delete(id, currentUser.require());
    }

    static AttachmentView view(Attachment a, boolean canDelete) {
        return new AttachmentView(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes(), a.isImage(),
                a.getUploadedByName(), a.getCreatedAt(), canDelete);
    }
}
