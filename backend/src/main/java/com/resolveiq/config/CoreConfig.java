package com.resolveiq.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Cross-cutting infrastructure beans.
 *
 * <p>A single injectable {@link Clock} is used everywhere "now" matters (SLA deadlines, escalation,
 * audit timestamps). Tests replace it with a controllable clock, so a 30-minute SLA breach can be
 * tested in milliseconds instead of waiting half an hour.</p>
 */
@Configuration
@EnableScheduling
public class CoreConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ResolveIQ I-ITSM API")
                        .version("1.0.0")
                        .description("Intelligent IT Service Management & Incident Resolution System. "
                                + "Use POST /api/auth/login, then click Authorize and paste the token.")
                        .contact(new Contact().name("ResolveIQ team")))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
