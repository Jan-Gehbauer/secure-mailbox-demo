package securemailbox.security;

import securemailbox.entity.RefreshToken;
import securemailbox.entity.User;
import securemailbox.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Prüft die Kernlogik der Refresh-Token-Rotation:
 * - gültiger Token -> wird widerrufen, neuer wird ausgestellt (Rotation)
 * - abgelaufener/bereits widerrufener/unbekannter Token -> abgelehnt
 *
 * Die konkrete Hash-Funktion (SHA-256) wird hier bewusst NICHT
 * mitgetestet - das wäre ein Test gegen ein Implementierungsdetail statt
 * gegen Verhalten. Die Mocks antworten deshalb auf "irgendeinen" Hash
 * (any()), nicht auf einen exakt nachgerechneten Wert.
 */
class RefreshTokenServiceTest {

    private RefreshTokenRepository repository;
    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        service = new RefreshTokenService(repository, 604_800_000L);

        user = new User();
        user.setId(1L);
        user.setUsername("alice");
    }

    @Test
    void issue_savesHashedTokenAndReturnsRawToken() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String rawToken = service.issue(user);

        assertThat(rawToken).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        // Der gespeicherte Hash darf NIE dem Klartext-Token entsprechen -
        // sonst würde ein DB-Leak allein gültige Sessions preisgeben.
        assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(saved.isRevoked()).isFalse();
    }

    @Test
    void issue_generatesDifferentTokensOnEachCall() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String first = service.issue(user);
        String second = service.issue(user);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rotate_withValidToken_revokesOldAndIssuesNewOne() {
        RefreshToken existing = new RefreshToken();
        existing.setUser(user);
        existing.setTokenHash("irrelevant-fuer-diesen-test");
        existing.setExpiresAt(Instant.now().plusSeconds(60));
        existing.setRevoked(false);

        when(repository.findByTokenHash(any())).thenReturn(Optional.of(existing));
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<RefreshTokenService.RotationResult> result = service.rotate("irgendein-raw-token");

        assertThat(result).isPresent();
        assertThat(result.get().user()).isEqualTo(user);
        assertThat(existing.isRevoked()).isTrue(); // der alte Token ist jetzt "verbrannt"

        // save() läuft zweimal: einmal für den Widerruf des alten Tokens,
        // einmal für das Anlegen des neuen (Rotation).
        verify(repository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void rotate_withAlreadyRevokedToken_isRejected_reuseDetection() {
        RefreshToken alreadyUsed = new RefreshToken();
        alreadyUsed.setUser(user);
        alreadyUsed.setTokenHash("hash");
        alreadyUsed.setExpiresAt(Instant.now().plusSeconds(60));
        alreadyUsed.setRevoked(true); // wurde bereits einmal für eine Rotation verwendet

        when(repository.findByTokenHash(any())).thenReturn(Optional.of(alreadyUsed));

        Optional<RefreshTokenService.RotationResult> result = service.rotate("bereits-verwendeter-token");

        assertThat(result).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void rotate_withExpiredToken_isRejected() {
        RefreshToken expired = new RefreshToken();
        expired.setUser(user);
        expired.setTokenHash("hash");
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        expired.setRevoked(false);

        when(repository.findByTokenHash(any())).thenReturn(Optional.of(expired));

        assertThat(service.rotate("abgelaufener-token")).isEmpty();
    }

    @Test
    void rotate_withUnknownToken_returnsEmpty() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThat(service.rotate("unbekannter-token")).isEmpty();
    }

    @Test
    void revoke_marksMatchingTokenAsRevoked() {
        RefreshToken existing = new RefreshToken();
        existing.setTokenHash("hash");
        existing.setRevoked(false);

        when(repository.findByTokenHash(any())).thenReturn(Optional.of(existing));

        service.revoke("irgendein-token");

        assertThat(existing.isRevoked()).isTrue();
        verify(repository).save(existing);
    }

    @Test
    void revoke_withUnknownToken_doesNothingSilently() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        // Darf keine Exception werfen - z.B. beim Logout mit bereits abgelaufenem Cookie
        service.revoke("unbekannter-token");

        verify(repository, never()).save(any());
    }

    @Test
    void revokeAllForUser_delegatesToRepository() {
        service.revokeAllForUser(user);

        verify(repository).revokeAllForUser(user);
    }
}
