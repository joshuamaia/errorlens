package dev.errorlens.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SensitiveDataSanitizerTest {

    @Test
    void redactsSensitiveHeadersKeepingScheme() {
        Map<String, String> headers = SensitiveDataSanitizer.headers(Map.of(
                "Authorization", List.of("Basic cnVubmVyOnJ1bm5lci1sYWI="),
                "Cookie", List.of("SESSION=abc"),
                "Accept", List.of("application/json")));
        assertThat(headers.get("Authorization")).isEqualTo("Basic [REDACTED]");
        assertThat(headers.get("Cookie")).isEqualTo("[REDACTED]");
        assertThat(headers.get("Accept")).isEqualTo("application/json");
    }

    @Test
    void masksSensitiveJsonFieldsStructurally() {
        String body = SensitiveDataSanitizer.body(
                "{\"name\":\"Ana\",\"password\":\"segredo\",\"nested\":{\"apiKey\":\"k\"},\"document\":\"111.444.777-35\"}",
                10_000);
        assertThat(body).contains("\"name\" : \"Ana\"").doesNotContain("segredo").doesNotContain("\"k\"")
                .doesNotContain("111.444.777-35");
    }

    @Test
    void sanitizesFreeTextAndTruncates() {
        String text = SensitiveDataSanitizer.text("token=abc123 Bearer eyJhbGciOiJIUzI1NiJ9.x.y "
                + "Key (email)=(ana@x.dev) already exists", 1000);
        assertThat(text).doesNotContain("abc123").doesNotContain("eyJhbGci").doesNotContain("ana@x.dev");
        assertThat(SensitiveDataSanitizer.truncate("x".repeat(50), 10)).startsWith("xxxxxxxxxx").contains("truncado");
    }

    @Test
    void keepsMalformedJsonForDiagnosis() {
        String malformed = "{ \"name\": \"x\",, ";
        assertThat(SensitiveDataSanitizer.body(malformed, 100)).isEqualTo(malformed);
    }
}
