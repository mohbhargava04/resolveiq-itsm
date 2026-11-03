package com.resolveiq.security;

import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.common.exception.EmailDomainNotAllowedException;
import com.resolveiq.settings.SettingKey;
import com.resolveiq.settings.SettingsService;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Only people with a company e-mail address may join. The allowed domains are a runtime setting
 * (Administration → Settings), so a company can add a domain after a merger without a redeploy.
 * Sub-domains count: allowing {@code bits-pilani.ac.in} also allows {@code hyderabad.bits-pilani.ac.in}.
 */
@Component
public class EmailDomainPolicy {

    private static final Pattern DOMAIN = Pattern.compile("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    private final SettingsService settings;

    public EmailDomainPolicy(SettingsService settings) {
        this.settings = settings;
    }

    public List<String> allowedDomains() {
        String raw = settings.get(SettingKey.ALLOWED_EMAIL_DOMAINS);
        return raw == null || raw.isBlank() ? List.of()
                : Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public boolean isAllowed(String email) {
        List<String> allowed = allowedDomains();
        if (allowed.isEmpty()) {
            return true;
        }
        String domain = domainOf(email);
        return domain != null && allowed.stream().anyMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    }

    public void require(String email) {
        if (!isAllowed(email)) {
            List<String> allowed = allowedDomains();
            throw new EmailDomainNotAllowedException("Use your company e-mail address"
                    + (allowed.size() == 1 ? " (@" + allowed.getFirst() + ")" : "") + " to join ResolveIQ.");
        }
    }

    public static String domainOf(String email) {
        if (email == null) {
            return null;
        }
        int at = email.lastIndexOf('@');
        return at < 0 ? null : email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
    }

    /** Validates and tidies an admin-entered list such as " Acme.com,  acme.co.uk ". */
    public static String normaliseList(String value) {
        List<String> parts = Arrays.stream(value.split("[,\\s]+")).map(s -> s.trim().toLowerCase(Locale.ROOT))
                .map(s -> s.startsWith("@") ? s.substring(1) : s).filter(s -> !s.isEmpty()).distinct().toList();
        for (String p : parts) {
            if (!DOMAIN.matcher(p).matches()) {
                throw new BusinessRuleViolationException("'" + p + "' is not a valid domain name");
            }
        }
        String joined = String.join(",", parts);
        if (joined.length() > 200) {
            throw new BusinessRuleViolationException("Too many domains");
        }
        return joined;
    }
}
