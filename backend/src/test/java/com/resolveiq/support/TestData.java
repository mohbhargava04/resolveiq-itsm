package com.resolveiq.support;

import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.Criticality;
import com.resolveiq.catalog.ItService;
import com.resolveiq.incident.domain.Actor;
import com.resolveiq.incident.domain.Classification;
import com.resolveiq.incident.domain.Impact;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.incident.domain.Priority;
import com.resolveiq.incident.domain.Urgency;
import com.resolveiq.priority.PriorityAssessment;
import com.resolveiq.sla.SlaPolicy;
import com.resolveiq.user.domain.Administrator;
import com.resolveiq.user.domain.Employee;
import com.resolveiq.user.domain.ExperienceLevel;
import com.resolveiq.user.domain.TeamLead;
import com.resolveiq.user.domain.Technician;
import com.resolveiq.user.domain.User;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Builders for plain domain objects in unit tests (no database). */
public final class TestData {

    private static final AtomicLong IDS = new AtomicLong(1000);
    public static final Instant T0 = Instant.parse("2026-10-20T04:30:00Z");

    private TestData() {
    }

    public static Employee employee(String name) {
        return withId(new Employee(name, name.toLowerCase().replace(' ', '.') + "@test.io", "hash", "+91 9000000000", "Dept"));
    }

    public static Technician technician(String name, ExperienceLevel level, String... skills) {
        return withId(new Technician(name, name.toLowerCase().replace(' ', '.') + "@test.io", "hash", "+91 9000000001",
                "IT", Set.of(skills), level, 5));
    }

    public static TeamLead teamLead(String name, String... skills) {
        return withId(new TeamLead(name, name.toLowerCase().replace(' ', '.') + "@test.io", "hash", "+91 9000000002",
                "IT", Set.of(skills), ExperienceLevel.EXPERT, 3));
    }

    public static Administrator admin(String name) {
        return withId(new Administrator(name, name.toLowerCase().replace(' ', '.') + "@test.io", "hash", null, "IT"));
    }

    public static ItService service(String code, Criticality criticality) {
        return withId(new ItService(code, code + " service", null, criticality, "Team", 100));
    }

    public static Category category(String code, boolean securitySensitive) {
        return withId(new Category(code, code.charAt(0) + code.substring(1).toLowerCase(), null, Set.of(), securitySensitive));
    }

    public static SlaPolicy policy(Priority priority, int response, int resolution) {
        return new SlaPolicy(null, priority, null, response, resolution, 75);
    }

    public static Incident newIncident(User reporter, ItService service, Instant at) {
        return new Incident("INC-" + IDS.incrementAndGet(), "Payment page down",
                "Users cannot pay fees", reporter, service, Impact.HIGH, Urgency.HIGH, 50, at);
    }

    /** A classified incident with the given priority and SLA (response/resolution minutes). */
    public static Incident classified(User reporter, Priority priority, int response, int resolution, Instant at) {
        Incident i = newIncident(reporter, service("PAYMENT", Criticality.CRITICAL), at);
        PriorityAssessment a = new PriorityAssessment(priority, 50, "test", List.of("test"));
        i.classify(new Classification(category("APPLICATION", false), false, null, a,
                policy(priority, response, resolution)), Actor.SYSTEM, at);
        return i;
    }

    /** Sets the private id field, as the database would. */
    public static <T> T withId(T entity) {
        try {
            Class<?> c = entity.getClass();
            while (c != null && !c.getSimpleName().equals("BaseEntity")) {
                c = c.getSuperclass();
            }
            Field f = c.getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, IDS.incrementAndGet());
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
