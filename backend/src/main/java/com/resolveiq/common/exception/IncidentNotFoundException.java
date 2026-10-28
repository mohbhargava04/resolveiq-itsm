package com.resolveiq.common.exception;

public class IncidentNotFoundException extends ResourceNotFoundException {

    public IncidentNotFoundException(String incidentNumber) {
        super(ErrorCode.INCIDENT_NOT_FOUND, "Incident '" + incidentNumber + "' was not found");
    }
}
