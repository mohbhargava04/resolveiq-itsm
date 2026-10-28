package com.resolveiq.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Seeds demo data on first start (when the user table is empty). Disable with ITSM_SEED_ENABLED=false. */
@Component
@ConditionalOnProperty(name = "itsm.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataRunner implements ApplicationRunner {

    private final DemoDataSeeder seeder;

    public DemoDataRunner(DemoDataSeeder seeder) {
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seeder.isEmpty()) {
            seeder.seed();
        }
    }
}
