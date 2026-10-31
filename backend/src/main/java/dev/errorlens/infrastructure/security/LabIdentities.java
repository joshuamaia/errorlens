package dev.errorlens.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Component;

import dev.errorlens.config.ErrorLensProperties;

/**
 * Identidades locais de demonstração. As senhas vêm da configuração (variáveis de ambiente)
 * e são armazenadas com BCrypt no {@code UserDetailsService}.
 */
@Component
public class LabIdentities {

    public static final String ADMIN = "admin";
    public static final String ANALYST = "analyst";
    public static final String VIEWER = "viewer";
    public static final String AUDITOR = "auditor";
    public static final String RUNNER = "runner";

    private final ErrorLensProperties.Users users;

    public LabIdentities(ErrorLensProperties properties) {
        this.users = properties.security().users();
    }

    public String passwordOf(String username) {
        return switch (username) {
            case ADMIN -> users.adminPassword();
            case ANALYST -> users.analystPassword();
            case VIEWER -> users.viewerPassword();
            case AUDITOR -> users.auditorPassword();
            case RUNNER -> users.runnerPassword();
            default -> throw new IllegalArgumentException("Identidade de laboratório desconhecida: " + username);
        };
    }

    /**
     * Header HTTP Basic para uma identidade de laboratório (usado apenas pelo Scenario Runner).
     */
    public String basicHeader(String username) {
        String raw = username + ":" + passwordOf(username);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
