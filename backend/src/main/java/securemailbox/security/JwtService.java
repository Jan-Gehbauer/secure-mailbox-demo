package securemailbox.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * Erstellt und validiert die (kurzlebigen) Access-Tokens als signierte JWTs.
 *
 * Bewusste Designentscheidungen:
 * - HMAC-SHA256 (HS256) mit einem Secret aus der Config statt asymmetrisch
 *   (RS256): für einen einzelnen Service, der Tokens selbst ausstellt UND
 *   validiert, reicht ein shared secret. RS256 würde erst dann Vorteile
 *   bringen, wenn andere Services die Tokens nur validieren (public key),
 *   ohne welche ausstellen zu können - z.B. bei Microservices.
 * - Kurze Lebensdauer (Minuten) für Access-Tokens: geht ein Access-Token
 *   verloren (z.B. XSS, Log-Leak), ist der Schaden zeitlich begrenzt. Die
 *   eigentliche "Session-Länge" steuert der separat verwaltete,
 *   widerrufbare Refresh-Token (siehe RefreshTokenService).
 * - Der Token enthält bewusst nur username + role als Claims, keine
 *   sensiblen Daten - ein JWT ist nur signiert, nicht verschlüsselt, und
 *   für den Client (bzw. jeden, der den Token sieht) lesbar.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret-base64}") String secretBase64,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs) {
        byte[] keyBytes = Base64.getDecoder().decode(secretBase64);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "app.jwt.secret-base64 muss mindestens 256 Bit (32 Bytes) lang sein");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public String generateAccessToken(String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    /**
     * Validiert Signatur + Ablauf und gibt die Claims zurück.
     * Wirft JwtException (u.a. ExpiredJwtException, SignatureException),
     * wenn der Token ungültig ist - der Aufrufer (JwtAuthenticationFilter)
     * fängt das ab und behandelt den Request als nicht authentifiziert.
     */
    public Claims parseAndValidate(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) throws JwtException {
        return parseAndValidate(token).getSubject();
    }
}
