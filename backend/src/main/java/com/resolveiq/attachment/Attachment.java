package com.resolveiq.attachment;

import com.resolveiq.common.BaseEntity;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A file attached to an incident (screenshot, log, error report). The bytes live in {@link AttachmentContent}. */
@Entity
@Table(name = "attachments")
public class Attachment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private User uploadedBy;

    @Column(name = "uploaded_by", nullable = false, length = 120)
    private String uploadedByName;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    protected Attachment() {
    }

    public Attachment(Incident incident, User uploadedBy, String fileName, String contentType, long sizeBytes, String sha256) {
        this.incident = incident;
        this.uploadedBy = uploadedBy;
        this.uploadedByName = uploadedBy == null ? "Email" : uploadedBy.getFullName();
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    public boolean isImage() {
        return contentType.startsWith("image/");
    }

    public Incident getIncident() {
        return incident;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public String getUploadedByName() {
        return uploadedByName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }
}
