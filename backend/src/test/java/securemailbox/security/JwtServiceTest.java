package securemailbox.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        byte[] secretBytes = new byte[32];
        new SecureRandom().nextBytes(secretBytes);
        String secretBase64 = Base64.getEncoder().encodeToString(secretBytes);

        jwtService = new JwtService(secretBase64, 900_000L);
    }

    @Test
    void generateAndParse_roundTripsUsernameCorrectly() {
        String token = jwtService.generateAccessToken("alice", "USER");

        String extractedUsername = jwtService.extractUsername(token);

        assertThat(extractedUsername).isEqualTo("alice");
    }

    @Test
    void parseAndValidate_rejectsTokenSignedWithDifferentSecret() {
        String token = jwtService.generateAccessToken("alice", "USER");

        byte[] otherSecretBytes = new byte[32];
        new SecureRandom().nextBytes(otherSecretBytes);
        JwtService otherService = new JwtService(
                Base64.getEncoder().encodeToString(otherSecretBytes), 900_000L);

        assertThatThrownBy(() -> otherService.extractUsername(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseAndValidate_rejectsExpiredToken() throws InterruptedException {
        JwtService shortLivedService = new JwtService(
                Base64.getEncoder().encodeToString(new byte[32]), 1L);

        String token = shortLivedService.generateAccessToken("alice", "USER");
        Thread.sleep(20);

        assertThatThrownBy(() -> shortLivedService.extractUsername(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void constructor_rejectsSecretShorterThan256Bit() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new JwtService(shortSecret, 900_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
