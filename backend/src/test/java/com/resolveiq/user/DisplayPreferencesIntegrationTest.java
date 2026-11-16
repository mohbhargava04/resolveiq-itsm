package com.resolveiq.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.resolveiq.support.IntegrationTestBase;
import com.resolveiq.user.domain.DisplayPreferences;
import com.resolveiq.user.domain.Employee;
import com.resolveiq.user.domain.TextSize;
import com.resolveiq.user.domain.ThemeChoice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisplayPreferencesIntegrationTest extends IntegrationTestBase {

    private static Map<String, Object> prefs(String theme, String textSize) {
        return Map.of("theme", theme, "textSize", textSize, "reduceMotion", true, "easierReading", true,
                "underlineLinks", false);
    }

    @Test
    @DisplayName("DSP-01 A new account starts with the defaults: system theme, default text size, no reading aids")
    void defaults() throws Exception {
        JsonNode display = get("ava", "/api/auth/me").body().get("display");
        assertThat(display.get("theme").asText()).isEqualTo("SYSTEM");
        assertThat(display.get("textSize").asText()).isEqualTo("DEFAULT");
        assertThat(display.get("reduceMotion").asBoolean()).isFalse();
        assertThat(new Employee("Ava", "ava@x.io", "h", null, null).getDisplayPreferences())
                .isEqualTo(DisplayPreferences.defaults());
    }

    @Test
    @DisplayName("DSP-02 Saved preferences are returned with the profile, so they follow the person to any device")
    void savedToAccount() throws Exception {
        Response saved = put("ava", "/api/auth/preferences", prefs("HIGH_CONTRAST", "EXTRA_LARGE"));
        assertThat(saved.status()).isEqualTo(200);
        JsonNode display = get("ava", "/api/auth/me").body().get("display");
        assertThat(display.get("theme").asText()).isEqualTo("HIGH_CONTRAST");
        assertThat(display.get("textSize").asText()).isEqualTo("EXTRA_LARGE");
        assertThat(display.get("reduceMotion").asBoolean()).isTrue();
        assertThat(display.get("easierReading").asBoolean()).isTrue();
        assertThat(display.get("underlineLinks").asBoolean()).isFalse();
        // one person's choice never changes anyone else's
        assertThat(get("rahul", "/api/auth/me").body().get("display").get("theme").asText()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("DSP-03 Unknown themes or sizes are rejected with 400, and signing in is required")
    void invalidAndAnonymous() throws Exception {
        assertThat(put("ava", "/api/auth/preferences", prefs("NEON", "DEFAULT")).status()).isEqualTo(400);
        assertThat(put("ava", "/api/auth/preferences", Map.of("theme", "DARK")).status()).isEqualTo(400);
        assertThat(put(null, "/api/auth/preferences", prefs("DARK", "LARGE")).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("DSP-04 DisplayPreferences is an immutable value object: equal by value, null parts refused")
    void valueObject() {
        DisplayPreferences a = new DisplayPreferences(ThemeChoice.DIM, TextSize.LARGE, false, true, true);
        assertThat(a).isEqualTo(new DisplayPreferences(ThemeChoice.DIM, TextSize.LARGE, false, true, true))
                .hasSameHashCodeAs(new DisplayPreferences(ThemeChoice.DIM, TextSize.LARGE, false, true, true))
                .isNotEqualTo(DisplayPreferences.defaults());
        assertThat(TextSize.EXTRA_LARGE.percent()).isEqualTo(125);
        assertThatThrownBy(() -> new DisplayPreferences(null, TextSize.LARGE, false, false, false))
                .isInstanceOf(NullPointerException.class);
    }
}
