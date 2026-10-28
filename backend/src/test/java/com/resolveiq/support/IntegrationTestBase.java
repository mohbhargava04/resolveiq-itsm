package com.resolveiq.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;


/**
 * Boots the whole application (H2 in-memory, demo data seeded) and drives it through HTTP with MockMvc.
 * Each test runs in a transaction that is rolled back, so tests do not affect each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(IntegrationTestBase.ClockConfig.class)
public abstract class IntegrationTestBase {

    public static final String PASSWORD = "Password@123";

    @TestConfiguration
    public static class ClockConfig {
        @Bean
        @Primary
        public MutableClock mutableClock() {
            return new MutableClock(Instant.now());
        }
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected MutableClock clock;

    private final Map<String, String> tokens = new HashMap<>();
    private Instant clockAtStart;

    @org.junit.jupiter.api.BeforeEach
    void rememberClock() {
        clockAtStart = clock.instant();
    }

    @AfterEach
    void resetClock() {
        clock.set(clockAtStart);
    }

    /** Logs in a demo user by first name (e.g. "ava", "rahul", "admin") and caches the JWT. */
    protected String token(String user) throws Exception {
        if (!tokens.containsKey(user)) {
            MvcResult r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", user + "@resolveiq.test", "password", PASSWORD))))
                    .andReturn();
            tokens.put(user, json.readTree(r.getResponse().getContentAsString()).get("token").asText());
        }
        return tokens.get(user);
    }

    protected Response call(String user, MockHttpServletRequestBuilder request) throws Exception {
        if (user != null) {
            request.header("Authorization", "Bearer " + token(user));
        }
        MvcResult r = mvc.perform(request).andReturn();
        String body = r.getResponse().getContentAsString();
        return new Response(r.getResponse().getStatus(), body.isBlank() ? null : json.readTree(body));
    }

    protected Response get(String user, String url) throws Exception {
        return call(user, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url));
    }

    protected Response post(String user, String url, Object body) throws Exception {
        MockHttpServletRequestBuilder req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            req.content(json.writeValueAsString(body));
        }
        return call(user, req);
    }

    protected Response put(String user, String url, Object body) throws Exception {
        return call(user, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    /** Demo e-mails are firstname@resolveiq.test. */
    protected static String userKey(JsonNode person) {
        return person.get("name").asText().split(" ")[0].toLowerCase();
    }

    public record Response(int status, JsonNode body) {
        public String code() {
            return body == null || body.get("code") == null ? null : body.get("code").asText();
        }
    }
}
