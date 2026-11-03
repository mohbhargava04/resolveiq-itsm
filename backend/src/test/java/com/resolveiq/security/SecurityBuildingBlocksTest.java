package com.resolveiq.security;

import com.resolveiq.attachment.FileTypeDetector;
import com.resolveiq.common.exception.BusinessRuleViolationException;
import com.resolveiq.config.AppProperties;
import com.resolveiq.config.DeploymentSafetyCheck;
import com.resolveiq.notification.inbound.EmailReplyParser;
import com.resolveiq.security.crypto.SecretCipher;
import com.resolveiq.security.mfa.Totp;
import com.resolveiq.security.ratelimit.TokenBucketRateLimiter;
import com.resolveiq.user.PasswordPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for the security building blocks (no Spring context). */
class SecurityBuildingBlocksTest {

    @Test
    @DisplayName("UNIT-01 TOTP matches the RFC 6238 test vector and tolerates one step of clock drift only")
    void totpRfcVector() {
        // RFC 6238 Appendix B: ASCII secret "12345678901234567890", T = 59 s -> 94287082 (8 digits) -> 287082 (6 digits)
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"; // Base32 of the RFC secret
        assertThat(Totp.codeAt(secret, Totp.stepAt(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        String current = Totp.codeAt(secret, Totp.stepAt(now));
        assertThat(Totp.verify(secret, current, now.plusSeconds(30))).isPresent();
        assertThat(Totp.verify(secret, current, now.plusSeconds(95))).isEmpty();
        assertThat(Totp.verify(secret, "12ab56", now)).isEmpty();
        assertThat(Totp.newSecret()).hasSize(32).matches("[A-Z2-7]+");
    }

    @Test
    @DisplayName("UNIT-02 AES-GCM secrets round-trip, use a fresh nonce each time, and detect tampering")
    void secretCipher() {
        SecretCipher cipher = new SecretCipher(new SecurityProperties("x".repeat(40), "k".repeat(40), 15, 7, 30, 20,
                5, 15, List.of(), 4, false));
        String a = cipher.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(a).startsWith("v1:").isNotEqualTo(cipher.encrypt("JBSWY3DPEHPK3PXP"));
        assertThat(cipher.decrypt(a)).isEqualTo("JBSWY3DPEHPK3PXP");
        char[] chars = a.toCharArray();
        chars[10] = chars[10] == 'A' ? 'B' : 'A';
        assertThatThrownBy(() -> cipher.decrypt(new String(chars))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("UNIT-03 Token bucket allows a burst up to capacity, then refills over time")
    void tokenBucket() {
        AtomicLong nanos = new AtomicLong();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(nanos::get);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("k", 5, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        TokenBucketRateLimiter.Decision refused = limiter.tryAcquire("k", 5, Duration.ofMinutes(1));
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfterSeconds()).isBetween(1L, 12L);
        nanos.addAndGet(Duration.ofSeconds(12).toNanos());
        assertThat(limiter.tryAcquire("k", 5, Duration.ofMinutes(1)).allowed()).isTrue();
        assertThat(limiter.tryAcquire("other", 5, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    @DisplayName("UNIT-04 Password policy rejects short, letter-only, breached and e-mail-based passwords")
    void passwordPolicy() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Ab1")).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("abcdefghij")).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("Password123")).hasMessageContaining("too common");
        assertThatThrownBy(() -> PasswordPolicy.validate("priya2026x", "priya@x.io")).hasMessageContaining("e-mail name");
        PasswordPolicy.validate("Lantern-Bay-61", "priya@x.io");
    }

    @Test
    @DisplayName("UNIT-05 Upload type detection trusts bytes, not names")
    void fileTypes() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
        assertThat(FileTypeDetector.detect(png, "a.png")).contains(FileTypeDetector.FileType.PNG);
        assertThat(FileTypeDetector.detect(png, "a.txt")).as("extension must match content").isEmpty();
        assertThat(FileTypeDetector.detect("%PDF-1.7".getBytes(), "r.pdf")).contains(FileTypeDetector.FileType.PDF);
        assertThat(FileTypeDetector.detect(new byte[]{'P', 'K', 3, 4, 0}, "a.zip")).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[]{'P', 'K', 3, 4, 0}, "a.docx")).contains(FileTypeDetector.FileType.DOCX);
        assertThat(FileTypeDetector.detect("<!DOCTYPE html><script>".getBytes(StandardCharsets.UTF_8), "a.txt")).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[]{'M', 'Z', 0, 1}, "a.exe")).isEmpty();
    }

    @Test
    @DisplayName("UNIT-06 Reply parsing finds the incident tag and strips quoted text")
    void replyParser() {
        assertThat(EmailReplyParser.incidentNumber("RE: Update on INC-1042 [inc-1042]")).contains("INC-1042");
        assertThat(EmailReplyParser.address("\"Ava S\" <AVA@resolveiq.test>")).contains("ava@resolveiq.test");
        assertThat(EmailReplyParser.newText("Fixed now, thanks!\n\nOn Mon, IT wrote:\n> old text")).isEqualTo("Fixed now, thanks!");
        assertThat(EmailReplyParser.newText("Line one\n> quoted\nLine two")).isEqualTo("Line one\nLine two");
        assertThat(EmailReplyParser.cleanSubject("Fwd: RE: Printer broken [INC-77]")).isEqualTo("Printer broken");
    }

    @Test
    @DisplayName("UNIT-07 Allowed-domain lists are validated; sub-domains of an allowed domain are accepted")
    void domains() {
        assertThat(EmailDomainPolicy.normaliseList("A.com, @b.co.uk  a.com")).isEqualTo("a.com,b.co.uk");
        assertThatThrownBy(() -> EmailDomainPolicy.normaliseList("not a domain!")).isInstanceOf(BusinessRuleViolationException.class);
        assertThat(EmailDomainPolicy.domainOf("x@Hyderabad.BITS-Pilani.ac.in")).isEqualTo("hyderabad.bits-pilani.ac.in");
    }

    @Test
    @DisplayName("UNIT-08 A real deployment refuses to start with the public development secrets")
    void deploymentSafetyCheck() {
        SecurityProperties devSecrets = new SecurityProperties("resolveiq-dev-only-secret-change-me-0123456789",
                "resolveiq-dev-only-data-key-change-me-0123456789", 15, 7, 30, 20, 5, 15, List.of(), 4, true);
        SecurityProperties realSecrets = new SecurityProperties("x".repeat(40), "k".repeat(40), 15, 7, 30, 20,
                5, 15, List.of(), 4, false);
        assertThatThrownBy(() -> new DeploymentSafetyCheck(new AppProperties("https://x.example", false), devSecrets))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ITSM_JWT_SECRET").hasMessageContaining("ITSM_DATA_KEY")
                .hasMessageContaining("ITSM_EXPOSE_DEV_TOOLS");
        new DeploymentSafetyCheck(new AppProperties("http://localhost:8080", true), devSecrets);   // demo: warns only
        new DeploymentSafetyCheck(new AppProperties("https://x.example", false), realSecrets);    // real: fine
    }
}
