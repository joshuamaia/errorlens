package dev.errorlens.application.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import dev.errorlens.config.ErrorLensProperties;

/**
 * Emissão e validação de tokens opacos de sessão para o frontend. Apenas o hash SHA-256
 * do token é mantido em memória; tokens expiram conforme {@code errorlens.security.token-ttl}.
 */
@Service
public class TokenService {

    private static final int MAX_TOKENS = 2000;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final ErrorLensProperties properties;

    public TokenService(ErrorLensProperties properties) {
        this.properties = properties;
    }

    public IssuedToken issue(Authentication authentication) {
        if (sessions.size() >= MAX_TOKENS) {
            evictExpired();
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        Instant expiresAt = Instant.now().plus(properties.security().tokenTtl());
        sessions.put(hash(token), new Session(authentication.getName(), roles, expiresAt));
        return new IssuedToken(token, authentication.getName(), roles, expiresAt);
    }

    public Optional<Session> resolve(String token) {
        Session session = sessions.get(hash(token));
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(hash(token));
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void revoke(String token) {
        sessions.remove(hash(token));
    }

    @Scheduled(fixedDelay = 300_000)
    void evictExpired() {
        Instant now = Instant.now();
        sessions.values().removeIf(session -> session.expiresAt().isBefore(now));
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Session(String username, List<String> roles, Instant expiresAt) {
    }

    public record IssuedToken(String token, String username, List<String> roles, Instant expiresAt) {
    }
}
