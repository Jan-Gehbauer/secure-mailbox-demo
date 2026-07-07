package securemailbox.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Server-seitig gespeicherter Refresh-Token-Zustand.
 *
 * Warum überhaupt in der DB, wenn Refresh-Tokens auch als reines JWT gehen
 * würden? Weil ein rein selbst-validierendes JWT nicht widerrufbar ist,
 * bevor es abläuft - bei "Logout" oder einem gestohlenen Token könnte man
 * es sonst nicht invalidieren. Deshalb: nur ein zufälliges Token-Secret
 * (nicht der Token selbst, siehe tokenHash) landet in der DB, und jeder
 * Refresh-Vorgang rotiert den Token (Token-Rotation) - wird ein bereits
 * benutzter/widerrufener Token nochmal vorgelegt, ist das ein klares
 * Signal für Missbrauch (siehe RefreshTokenService.rotate()).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // SHA-256-Hash des eigentlichen Tokens (nicht das Token selbst!) -
    // so bringt ein DB-Leak allein keinem Angreifer gültige Sessions.
    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false)
    private Instant createdAt;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isValid() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }
}
