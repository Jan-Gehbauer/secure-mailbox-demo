package securemailbox.controller;

import securemailbox.dto.AuthResponse;
import securemailbox.dto.LoginRequest;
import securemailbox.dto.RegisterRequest;
import securemailbox.entity.User;
import securemailbox.repository.UserRepository;
import securemailbox.security.JwtService;
import securemailbox.security.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;

/**
 * Alle Auth-Endpunkte sind über SecurityConfig explizit als "öffentlich"
 * markiert (/api/auth/**) - alles andere in der Anwendung verlangt einen
 * gültigen Access-Token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final boolean secureCookies;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            @Value("${app.cookies.secure}") boolean secureCookies) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Benutzername bereits vergeben");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-Mail-Adresse bereits registriert");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(User.Role.USER);
        userRepository.save(user);

        return issueTokens(user);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException e) {
            // Bewusst dieselbe Fehlermeldung wie bei unbekanntem Usernamen
            // (siehe GlobalExceptionHandler) - so lässt sich über die
            // Login-Antwort nicht per User-Enumeration herausfinden, welche
            // Benutzernamen existieren.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ungültige Anmeldedaten");
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ungültige Anmeldedaten"));

        return issueTokens(user);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String rawToken = readRefreshCookie(request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Kein Refresh-Token vorhanden"));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh-Token ungültig oder abgelaufen"));

        return buildAuthResponse(result.user(), result.newRawToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        readRefreshCookie(request).ifPresent(refreshTokenService::revoke);

        ResponseCookie expired = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();

        return ResponseEntity.noContent()
                .header("Set-Cookie", expired.toString())
                .build();
    }

    private ResponseEntity<AuthResponse> issueTokens(User user) {
        String rawRefreshToken = refreshTokenService.issue(user);
        return buildAuthResponse(user, rawRefreshToken);
    }

    private ResponseEntity<AuthResponse> buildAuthResponse(User user, String rawRefreshToken) {
        String accessToken = jwtService.generateAccessToken(user.getUsername(), user.getRole().name());

        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)                     // per JS nicht lesbar -> kein XSS-Zugriff aufs Refresh-Token
                .secure(secureCookies)               // nur über HTTPS (in lokaler Dev-Umgebung ausschaltbar)
                .sameSite("Strict")                  // wird bei Cross-Site-Requests gar nicht erst mitgeschickt -> CSRF-Schutz
                .path("/api/auth")                   // Cookie nur für die Auth-Endpunkte sichtbar, nicht für /api/messages
                .maxAge(Duration.ofMillis(refreshTokenService.getRefreshTokenExpirationMs()))
                .build();

        AuthResponse body = new AuthResponse(
                accessToken,
                jwtService.getAccessTokenExpirationMs() / 1000,
                user.getUsername(),
                user.getRole().name()
        );

        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie.toString())
                .body(body);
    }

    private Optional<String> readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
