package com.resolveiq.incident;

import com.resolveiq.audit.AuditAction;
import com.resolveiq.audit.AuditService;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.ItsmException;
import com.resolveiq.common.exception.UnauthorizedOperationException;
import com.resolveiq.incident.domain.Actor;
import com.resolveiq.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Applies one action to many incidents (team leads triaging a queue). Each incident is processed in
 * its own transaction through the normal {@link IncidentService} command, so every business rule and
 * audit entry still applies, and one incident that cannot be changed does not undo the others. The
 * result lists the outcome per incident.
 */
@Service
public class BulkActionService {

    public static final int MAX_ITEMS = 100;

    public enum Action { ASSIGN, LINK_TO_MAJOR, MARK_DUPLICATE, RESOLVE, CLOSE }

    public record ItemResult(String number, boolean ok, String message) {
    }

    private final IncidentService incidents;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public BulkActionService(IncidentService incidents, AuditService audit, PlatformTransactionManager txManager) {
        this.incidents = incidents;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
    }

    public List<ItemResult> apply(List<String> numbers, Action action, Map<String, String> params, User actor) {
        if (!Actor.of(actor).isManagement()) {
            throw new UnauthorizedOperationException("Bulk actions are for team leads and administrators");
        }
        if (numbers == null || numbers.isEmpty()) {
            throw new BusinessRuleViolationException("Select at least one incident");
        }
        if (numbers.size() > MAX_ITEMS) {
            throw new BusinessRuleViolationException("At most " + MAX_ITEMS + " incidents at a time");
        }
        Map<String, String> p = params == null ? Map.of() : params;
        List<ItemResult> results = new ArrayList<>();
        for (String number : new LinkedHashSet<>(numbers)) {
            try {
                tx.executeWithoutResult(status -> run(number, action, p, actor));
                results.add(new ItemResult(number, true, "Done"));
            } catch (ItsmException ex) {
                results.add(new ItemResult(number, false, ex.getMessage()));
            }
        }
        long ok = results.stream().filter(ItemResult::ok).count();
        audit.recordAdminAction(actor, AuditAction.BULK_ACTION, null, action.name(),
                "Bulk " + action.name().toLowerCase().replace('_', ' ') + ": " + ok + " of " + results.size() + " incidents updated");
        return results;
    }

    private void run(String number, Action action, Map<String, String> p, User actor) {
        switch (action) {
            case ASSIGN -> {
                String tech = p.get("technicianId");
                incidents.assign(number, tech == null || tech.isBlank() ? null : Long.valueOf(tech), p.get("strategy"), actor);
            }
            case LINK_TO_MAJOR -> incidents.linkToMajor(number, require(p, "majorNumber"), actor);
            case MARK_DUPLICATE -> incidents.markDuplicate(number, require(p, "parentNumber"), actor);
            case RESOLVE -> incidents.resolve(number, require(p, "summary"), p.get("rootCause"), actor);
            case CLOSE -> incidents.close(number, p.get("note"), actor);
        }
    }

    private static String require(Map<String, String> p, String key) {
        String v = p.get(key);
        if (v == null || v.isBlank()) {
            throw new BusinessRuleViolationException("Missing " + key);
        }
        return v;
    }
}
