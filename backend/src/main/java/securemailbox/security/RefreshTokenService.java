package securemailbox.security;

import securemailbox.entity.RefreshToken;
import securemailbox.entity.User;
import securemailbox.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Verwaltet die Refresh-Tokens: langlebiger als Access-Tokens, aber im
 * Gegensatz zu diesen serverseitig widerrufbar und rotierend.
 *
 * Ablauf beim Refresh (siehe rotate()):
 * 1. Client schickt den aktuellen Refresh-Token (aus dem httpOnly-Cookie).
 * 2. Wir schlagen dessen Hash in der DB nach.
 * 3. Ist er gültig (nicht abgelaufen, nicht widerrufen): wir markieren ihn
 *    als widerrufen und stellen einen NEUEN aus (Rotation). So ist jeder
 *    Refresh-Token nur genau einmal verwendbar ("one-time use").
 * 4. Wird ein bereits widerrufener Token nochmal vorgelegt, ist das ein
 *    starkes Indiz für Token-Diebstahl (Replay) - in einem produktiven
 *    System würde man hier zusätzlich alle Tokens der/des Betroffenen
 *    invalidieren ("refresh token reuse detection"). Für die Demo reicht
 *    die Ablehnung des Requests.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshTokenExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.repository = repository;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    /** Erstellt einen neuen Refresh-Token für den Benutzer und gibt den Klartext-Token zurück. */
    public String issue(User user) {
        String rawToken = generateRawToken();

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(Instant.now().plusMillis(refreshTokenExpirationMs));
        repository.save(entity);

        return rawToken;
    }

    /**
     * Validiert den vorgelegten Token und rotiert ihn: der alte wird
     * widerrufen, ein neuer ausgestellt. Gibt leer zurück, wenn der Token
     * ungültig/abgelaufen/bereits widerrufen ist.
     */
    public Optional<RotationResult> rotate(String rawToken) {
        Optional<RefreshToken> existing = repository.findByTokenHash(hash(rawToken));
        if (existing.isEmpty() || !existing.get().isValid()) {
            return Optional.empty();
        }

        RefreshToken old = existing.get();
        old.setRevoked(true);
        repository.save(old);

        String newRawToken = issue(old.getUser());
        return Optional.of(new RotationResult(old.getUser(), newRawToken));
    }

    /** Widerruft (Logout) einen einzelnen Token, falls vorhanden. */
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(t -> {
            t.setRevoked(true);
            repository.save(t);
        });
    }

    /** Widerruft alle Refresh-Tokens eines Users, z.B. bei Passwortänderung. */
    public void revokeAllForUser(User user) {
        repository.revokeAllForUser(user);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 ist in jeder JVM garantiert vorhanden - dieser Zweig ist unerreichbar.
            throw new IllegalStateException(e);
        }
    }

    public record RotationResult(User user, String newRawToken) {}
}
