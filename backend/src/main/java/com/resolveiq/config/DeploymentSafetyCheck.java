package com.resolveiq.config;

import com.resolveiq.security.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start a real (non-demo) deployment that still uses the development secrets shipped in
 * {@code application.yml}. Anyone who has read the source code knows those values, so with them an
 * attacker could forge sign-in tokens or decrypt stored two-factor secrets (OWASP Top 10:2025 A02/A04).
 *
 * <p>In demo mode the problems are only logged, so the project still runs with no setup at all.</p>
 */
@Component
public class DeploymentSafetyCheck {

    private static final Logger log = LoggerFactory.getLogger(DeploymentSafetyCheck.class);
    private static final String DEV_MARKER = "dev-only";

    public DeploymentSafetyCheck(AppProperties app, SecurityProperties security) {
        List<String> problems = problems(app, security);
        if (problems.isEmpty()) {
            return;
        }
        if (app.isDemo()) {
            problems.forEach(p -> log.warn("Demo mode: {} (fine for a demo, not for a real deployment)", p));
        } else {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", problems));
        }
    }

    static List<String> problems(AppProperties app, SecurityProperties security) {
        List<String> problems = new ArrayList<>();
        if (security.jwtSecret().contains(DEV_MARKER)) {
            problems.add("ITSM_JWT_SECRET is the public development value");
        }
        if (security.dataKey().contains(DEV_MARKER)) {
            problems.add("ITSM_DATA_KEY is the public development value");
        }
        if (Boolean.TRUE.equals(security.exposeDevTools())) {
            problems.add("ITSM_EXPOSE_DEV_TOOLS opens Swagger UI and the H2 console to anyone");
        }
        return problems;
    }
}
