package com.resolveiq.intelligence;

import com.resolveiq.incident.domain.IncidentStatus;
import com.resolveiq.incident.event.IncidentClassified;
import com.resolveiq.incident.event.IncidentEvent;
import com.resolveiq.incident.event.IncidentObserver;
import com.resolveiq.incident.event.IncidentStatusChanged;
import org.springframework.stereotype.Component;

/**
 * Observer that keeps the machine-learning models fresh: whenever an incident gains a category or is
 * resolved, the classifier is marked for retraining and the SLA-risk statistics are refreshed.
 * This is how the system "learns" from its own history.
 */
@Component
public class KnowledgeBaseObserver implements IncidentObserver {

    private final CategoryClassifier classifier;
    private final SlaRiskAssessor riskAssessor;

    public KnowledgeBaseObserver(CategoryClassifier classifier, SlaRiskAssessor riskAssessor) {
        this.classifier = classifier;
        this.riskAssessor = riskAssessor;
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public void onIncidentEvent(IncidentEvent event) {
        if (event instanceof IncidentClassified) {
            classifier.markStale();
        } else if (event instanceof IncidentStatusChanged change && change.to() == IncidentStatus.RESOLVED) {
            classifier.markStale();
            riskAssessor.invalidate();
        }
    }
}
