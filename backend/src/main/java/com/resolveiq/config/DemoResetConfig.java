package com.resolveiq.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * When {@code itsm.seed.reset-on-start=true} (env {@code ITSM_RESET_DEMO=true}) the schema is dropped
 * and re-created on start-up, so the demo scenario is rebuilt with timestamps relative to "now".
 * Off by default: normally data persists across restarts.
 */
@Configuration
@ConditionalOnProperty(name = "itsm.seed.reset-on-start", havingValue = "true")
public class DemoResetConfig {

    private static final Logger log = LoggerFactory.getLogger(DemoResetConfig.class);

    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            log.warn("ITSM_RESET_DEMO=true: wiping the database and rebuilding the demo data");
            org.flywaydb.core.Flyway.configure()
                    .configuration(flyway.getConfiguration())
                    .cleanDisabled(false)
                    .load()
                    .clean();
            flyway.migrate();
        };
    }
}
