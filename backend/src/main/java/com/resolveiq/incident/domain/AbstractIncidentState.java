package com.resolveiq.incident.domain;

import com.resolveiq.common.exception.InvalidStateTransitionException;
import com.resolveiq.user.domain.Technician;

import java.time.Instant;

/**
 * Default behaviour for every state: refuse. Concrete states override only what they allow
 * ("deny by default"), which makes forbidden transitions such as NEW -> CLOSED impossible
 * by construction.
 */
abstract class AbstractIncidentState implements IncidentState {

    protected InvalidStateTransitionException reject(String action) {
        return new InvalidStateTransitionException(status().name(), action);
    }

    @Override
    public void classify(Incident incident, Classification classification, Actor actor, Instant at) {
        throw reject("CLASSIFY");
    }

    @Override
    public void assign(Incident incident, Technician technician, String strategy, Actor actor, Instant at) {
        throw reject("ASSIGN");
    }

    @Override
    public void acknowledge(Incident incident, Actor actor, Instant at) {
        throw reject("ACKNOWLEDGE");
    }

    @Override
    public void startWork(Incident incident, Actor actor, Instant at) {
        throw reject("START_WORK_ON");
    }

    @Override
    public void requestInformation(Incident incident, String question, Actor actor, Instant at) {
        throw reject("REQUEST_INFORMATION_FOR");
    }

    @Override
    public void provideInformation(Incident incident, String answer, Actor actor, Instant at) {
        throw reject("PROVIDE_INFORMATION_FOR");
    }

    @Override
    public void resolve(Incident incident, Resolution resolution, Actor actor, Instant at) {
        throw reject("RESOLVE");
    }

    @Override
    public void verify(Incident incident, Actor actor, Instant at) {
        throw reject("VERIFY");
    }

    @Override
    public void reopen(Incident incident, String reason, Actor actor, Instant at) {
        throw reject("REOPEN");
    }

    @Override
    public void close(Incident incident, String note, Actor actor, Instant at) {
        throw reject("CLOSE");
    }

    @Override
    public void closeAsDuplicate(Incident incident, Incident parent, Actor actor, Instant at) {
        throw reject("CLOSE_AS_DUPLICATE");
    }

    @Override
    public boolean allowsDetailEdits() {
        return false;
    }

    @Override
    public boolean allowsReprioritisation() {
        return false;
    }

    @Override
    public boolean acceptsNotes() {
        return true;
    }

    @Override
    public boolean allowsEscalation() {
        return false;
    }

    @Override
    public boolean acceptsFeedback() {
        return false;
    }

    @Override
    public String toString() {
        return status().name();
    }
}
