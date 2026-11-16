package com.resolveiq.assistant;

import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Urgency;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * What the assistant has learnt so far about the problem being reported. Embedded in the
 * {@link ChatSession} (composition). Besides the incident fields it remembers which checks were
 * already offered, so nothing is asked twice.
 *
 * <p>There are no setters: each change is a named operation ({@link #describe}, {@link #chooseService},
 * {@link #forgetService}) that keeps related fields consistent - for example changing the service
 * re-opens the major-incident and duplicate checks, which depend on it.</p>
 */
@Embeddable
public class IncidentDraft {

    public static final int MAX_TITLE = 120;
    public static final int MAX_DESCRIPTION = 3500;

    @Column(name = "draft_title", length = 160)
    private String title;

    @Column(name = "draft_description", length = 4000)
    private String description;

    @Column(name = "draft_service_code", length = 40)
    private String serviceCode;

    @Column(name = "draft_service_confirmed", nullable = false)
    private boolean serviceConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_impact", length = 20)
    private Impact impact;

    @Enumerated(EnumType.STRING)
    @Column(name = "draft_urgency", length = 20)
    private Urgency urgency;

    @Column(name = "draft_device", length = 80)
    private String device;

    @Column(name = "device_asked", nullable = false)
    private boolean deviceAsked;

    @Column(name = "major_checked", nullable = false)
    private boolean majorChecked;

    @Column(name = "self_help_checked", nullable = false)
    private boolean selfHelpChecked;

    @Column(name = "duplicates_checked", nullable = false)
    private boolean duplicatesChecked;

    /** The person saw the duplicate warning and chose to report anyway. */
    @Column(name = "duplicates_acknowledged", nullable = false)
    private boolean duplicatesAcknowledged;

    protected IncidentDraft() {
        // for JPA and for a fresh session
    }

    static IncidentDraft empty() {
        return new IncidentDraft();
    }

    // ---- behaviour -----------------------------------------------------------------------------

    /** Records the person's own words; the title is their first sentence. */
    public void describe(String text) {
        String clean = text.trim().replaceAll("[ \\t]+", " ");
        this.description = clean.length() > MAX_DESCRIPTION ? clean.substring(0, MAX_DESCRIPTION) : clean;
        this.title = titleFrom(this.description);
    }

    /** A best guess that still has to be confirmed by the person. */
    public void suggestService(String code) {
        this.serviceCode = code;
        this.serviceConfirmed = false;
    }

    public void chooseService(String code) {
        boolean changed = code != null && !code.equalsIgnoreCase(serviceCode);
        this.serviceCode = code;
        this.serviceConfirmed = true;
        if (changed) {
            reopenServiceChecks();
        }
    }

    /** "Change the service" from the summary: ask again, and redo the checks that depend on it. */
    public void forgetService() {
        this.serviceCode = null;
        this.serviceConfirmed = false;
        reopenServiceChecks();
    }

    private void reopenServiceChecks() {
        this.majorChecked = false;
        this.duplicatesChecked = false;
        this.duplicatesAcknowledged = false;
    }

    public void rateImpact(Impact impact) {
        this.impact = impact;
    }

    public void rateUrgency(Urgency urgency) {
        this.urgency = urgency;
    }

    public void noteDevice(String device) {
        String d = device == null ? null : device.trim();
        this.device = d == null || d.isEmpty() ? null : (d.length() > 80 ? d.substring(0, 80) : d);
        this.deviceAsked = true;
    }

    public void skipDevice() {
        this.deviceAsked = true;
    }

    public void markMajorChecked() {
        this.majorChecked = true;
    }

    public void markSelfHelpChecked() {
        this.selfHelpChecked = true;
    }

    public void markDuplicatesChecked(boolean reportAnyway) {
        this.duplicatesChecked = true;
        this.duplicatesAcknowledged = reportAnyway;
    }

    /** The pipeline refused the report as a duplicate of the person's own: show the warning (again). */
    public void reopenDuplicateCheck() {
        this.duplicatesChecked = false;
    }

    /** Description as it will be stored on the incident: the person's words plus the device, if given. */
    public String fullDescription() {
        return device == null ? description : description + "\n\nDevice: " + device;
    }

    static String titleFrom(String description) {
        String first = description.split("(?<=[.!?])\\s+|\\R", 2)[0].trim();
        if (first.length() < 8) {
            first = description.replaceAll("\\R+", " ");
        }
        first = first.replaceAll("[.!?]+$", "");
        if (first.length() > MAX_TITLE) {
            int cut = first.lastIndexOf(' ', MAX_TITLE - 1);
            first = first.substring(0, cut > 40 ? cut : MAX_TITLE - 1) + "…";
        }
        return Character.toUpperCase(first.charAt(0)) + first.substring(1);
    }

    // ---- accessors -----------------------------------------------------------------------------

    public boolean hasDescription() {
        return description != null;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public boolean isServiceConfirmed() {
        return serviceConfirmed;
    }

    public Impact getImpact() {
        return impact;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public String getDevice() {
        return device;
    }

    public boolean isDeviceAsked() {
        return deviceAsked;
    }

    public boolean isMajorChecked() {
        return majorChecked;
    }

    public boolean isSelfHelpChecked() {
        return selfHelpChecked;
    }

    public boolean isDuplicatesChecked() {
        return duplicatesChecked;
    }

    public boolean isDuplicatesAcknowledged() {
        return duplicatesAcknowledged;
    }
}
