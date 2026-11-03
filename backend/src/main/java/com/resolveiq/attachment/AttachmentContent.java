package com.resolveiq.attachment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** File bytes, in their own table so listing attachments never loads them. */
@Entity
@Table(name = "attachment_contents")
public class AttachmentContent {

    @Id
    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(nullable = false)
    private byte[] data;

    protected AttachmentContent() {
    }

    public AttachmentContent(Long attachmentId, byte[] data) {
        this.attachmentId = attachmentId;
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }
}
