package com.resolveiq.intelligence;

import com.resolveiq.catalog.Category;
import com.resolveiq.catalog.CategoryRepository;
import com.resolveiq.catalog.Criticality;
import com.resolveiq.catalog.ItService;
import com.resolveiq.incident.IncidentRepository;
import com.resolveiq.incident.domain.Incident;
import com.resolveiq.intelligence.text.SimilarityEngine;
import com.resolveiq.intelligence.text.TextAnalyzer;
import com.resolveiq.user.domain.Employee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static com.resolveiq.support.TestData.T0;
import static com.resolveiq.support.TestData.employee;
import static com.resolveiq.support.TestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntelligenceTest {

    @Test
    @DisplayName("Text analysis normalises synonyms: 'transaction failing' and 'unable to pay' mean payment outage")
    void synonyms() {
        assertThat(TextAnalyzer.analyze("Payment transaction failing")).containsExactly("payment", "payment", "outage");
        assertThat(TextAnalyzer.analyze("Unable to make payment")).contains("outage", "payment");
        assertThat(TextAnalyzer.analyze("the and of")).isEmpty();
    }

    @Test
    @DisplayName("TC-25 Duplicate detection: the brief's three payment reports are similar; a printer jam is not")
    void briefDuplicateExample() {
        SimilarityEngine engine = new SimilarityEngine();
        List<String> open = List.of("Payment application unavailable", "Payment transaction failing", "Printer paper jam");
        List<SimilarityEngine.Scored<String>> ranked = engine.rank("Unable to make payment", open, s -> s);
        assertThat(ranked.get(0).item()).startsWith("Payment");
        assertThat(ranked.get(1).item()).startsWith("Payment");
        assertThat(ranked.get(0).similarity()).isGreaterThan(0.5);
        assertThat(ranked).noneMatch(s -> s.item().startsWith("Printer"));
    }

    @Test
    @DisplayName("Service affinity: same service 1.0, dependent service 0.5, unrelated 0")
    void serviceAffinity() {
        ItService db = withId(new ItService("CORE_DB", "DB", null, Criticality.CRITICAL, "t", 1));
        ItService pay = withId(new ItService("PAYMENT", "Pay", null, Criticality.CRITICAL, "t", 1));
        ItService print = withId(new ItService("PRINT", "Print", null, Criticality.LOW, "t", 1));
        pay.addDependency(db);
        assertThat(DuplicateDetector.serviceAffinity(pay, pay)).isEqualTo(1.0);
        assertThat(DuplicateDetector.serviceAffinity(pay, db)).isEqualTo(0.5);
        assertThat(DuplicateDetector.serviceAffinity(pay, print)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TC-26 Naive Bayes classifier learns categories from keywords and returns calibrated confidence")
    void classifier() {
        CategoryRepository categories = mock(CategoryRepository.class);
        IncidentRepository incidents = mock(IncidentRepository.class);
        when(categories.findByActiveTrueOrderByName()).thenReturn(List.of(
                new Category("NETWORK", "Network", "wifi and vpn", Set.of("wifi", "vpn", "internet", "dns"), false),
                new Category("AUTHENTICATION", "Authentication", "login", Set.of("login", "password", "sso", "mfa"), false),
                new Category("HARDWARE", "Hardware", "devices", Set.of("laptop", "keyboard", "monitor", "printer"), false)));
        when(incidents.findAllCategorised()).thenReturn(List.of());
        NaiveBayesCategoryClassifier nb = new NaiveBayesCategoryClassifier(categories, incidents);

        CategorySuggestion login = nb.classify("I cannot log in, SSO keeps asking for my password");
        assertThat(login.categoryCode()).isEqualTo("AUTHENTICATION");
        assertThat(login.confidence()).isBetween(0.5, 0.999);
        assertThat(login.signals()).contains("login");

        assertThat(nb.classify("Wi-Fi is down in the hostel").categoryCode()).isEqualTo("NETWORK");
        assertThat(nb.classify("my laptop keyboard is broken").categoryCode()).isEqualTo("HARDWARE");
        assertThat(nb.classify("zzz qqq").categoryCode()).isNull(); // no known words -> no guess
    }

    @Test
    @DisplayName("TC-27 Correlation finds the most specific shared dependency, not the core network")
    void correlation() {
        ItService network = withId(new ItService("NETWORK_CORE", "Network", null, Criticality.CRITICAL, "t", 1));
        ItService db = withId(new ItService("CORE_DB", "Core DB", null, Criticality.CRITICAL, "t", 1));
        ItService pay = withId(new ItService("PAYMENT", "Payment", null, Criticality.CRITICAL, "t", 1));
        ItService hr = withId(new ItService("HR", "HR Portal", null, Criticality.MEDIUM, "t", 1));
        ItService erp = withId(new ItService("ERP", "ERP", null, Criticality.HIGH, "t", 1));
        ItService wifi = withId(new ItService("WIFI", "Wi-Fi", null, Criticality.MEDIUM, "t", 1));
        ItService print = withId(new ItService("PRINT", "Printing", null, Criticality.LOW, "t", 1));
        db.addDependency(network);
        pay.addDependency(db);
        hr.addDependency(db);
        erp.addDependency(db);
        wifi.addDependency(network);
        print.addDependency(network);

        Employee e = employee("Ava");
        List<Incident> open = List.of(
                incidentOn(e, pay, 30), incidentOn(e, hr, 20), incidentOn(e, erp, 10), incidentOn(e, wifi, 5));

        var clusters = IncidentCorrelator.correlate(open, List.of(network, db, pay, hr, erp, wifi, print), 3);
        assertThat(clusters).hasSize(1);
        assertThat(clusters.getFirst().rootServiceCode()).isEqualTo("CORE_DB");
        assertThat(clusters.getFirst().incidentNumbers()).hasSize(3);
    }

    @Test
    @DisplayName("Unrelated incidents produce no correlation")
    void noCorrelation() {
        ItService a = withId(new ItService("A", "A", null, Criticality.LOW, "t", 1));
        ItService b = withId(new ItService("B", "B", null, Criticality.LOW, "t", 1));
        Employee e = employee("Ava");
        var clusters = IncidentCorrelator.correlate(List.of(incidentOn(e, a, 3), incidentOn(e, b, 2)),
                List.of(a, b), 3);
        assertThat(clusters).isEmpty();
    }

    @Test
    @DisplayName("Median used by SLA risk prediction")
    void median() {
        assertThat(SlaRiskAssessor.median(List.of(10L, 30L, 20L))).isEqualTo(20.0);
        assertThat(SlaRiskAssessor.median(List.of(10L, 20L))).isEqualTo(15.0);
    }

    private static Incident incidentOn(Employee e, ItService s, int minutesAgo) {
        return new Incident("INC-" + s.getCode() + minutesAgo, "Failure on " + s.getName(), "It is failing", e, s,
                com.resolveiq.incident.domain.Impact.HIGH, com.resolveiq.incident.domain.Urgency.HIGH, 10,
                T0.minus(Duration.ofMinutes(minutesAgo)));
    }
}
