package com.resolveiq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

/**
 * Entry point of ResolveIQ, an Intelligent IT Service Management and Incident Resolution System.
 *
 * <p>Package layout follows the business capabilities of the system (incident, sla, assignment, ...)
 * rather than technical layers, so each capability can evolve independently.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ResolveIqApplication {

    static {
        // All timestamps are stored and compared in UTC. This also avoids PostgreSQL rejecting legacy JVM
        // zone names such as "Asia/Calcutta". Times are shown to users in itsm.display-zone (Asia/Kolkata).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ResolveIqApplication.class, args);
    }
}
