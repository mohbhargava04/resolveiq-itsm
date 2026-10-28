package com.resolveiq.incident.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * <b>Observer pattern</b> - the subject. Keeps the list of observers and fans every domain event out
 * to them. Spring discovers all {@link IncidentObserver} beans at start-up; observers can also be
 * attached/detached at runtime (used in tests).
 *
 * <p>A {@link CopyOnWriteArrayList} is used because events are published far more often than
 * observers are added, and publishing may happen concurrently (HTTP threads + SLA monitor).</p>
 */
@Component
public class IncidentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventPublisher.class);

    private final List<IncidentObserver> observers = new CopyOnWriteArrayList<>();

    public IncidentEventPublisher(List<IncidentObserver> initialObservers) {
        initialObservers.forEach(this::subscribe);
    }

    public final void subscribe(IncidentObserver observer) {
        observers.add(observer);
        observers.sort(Comparator.comparingInt(IncidentObserver::order));
    }

    public void unsubscribe(IncidentObserver observer) {
        observers.remove(observer);
    }

    public void publish(IncidentEvent event) {
        for (IncidentObserver observer : observers) {
            if (observer.isCritical()) {
                observer.onIncidentEvent(event);
                continue;
            }
            try {
                observer.onIncidentEvent(event);
            } catch (RuntimeException ex) {
                log.warn("Observer {} failed on {} for {}: {}", observer.getClass().getSimpleName(),
                        event.type(), event.incident().getNumber(), ex.getMessage());
            }
        }
    }

    public void publishAll(Collection<IncidentEvent> events) {
        events.forEach(this::publish);
    }

    public int observerCount() {
        return observers.size();
    }
}
