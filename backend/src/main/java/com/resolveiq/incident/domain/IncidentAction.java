package com.resolveiq.incident.domain;

/** Operations a user may be offered on an incident (drives which buttons the UI shows). */
public enum IncidentAction {
    EDIT,
    ASSIGN,
    ACKNOWLEDGE,
    START_WORK,
    REQUEST_INFO,
    PROVIDE_INFO,
    RESOLVE,
    VERIFY,
    REOPEN,
    CLOSE,
    ADD_NOTE,
    ADD_INTERNAL_NOTE,
    REPRIORITISE,
    ESCALATE,
    MARK_DUPLICATE,
    FEEDBACK,
    DECLARE_MAJOR,
    LINK_TO_MAJOR,
    POST_MAJOR_UPDATE,
    PUBLISH_ARTICLE
}
