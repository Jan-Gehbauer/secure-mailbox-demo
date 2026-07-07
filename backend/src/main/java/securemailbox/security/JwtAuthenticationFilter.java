package securemailbox.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Liest pro Request den "Authorization: Bearer <token>"-Header, validiert
 * das JWT und setzt bei Erfolg die Authentication in den SecurityContext.
 *
 * Läuft genau einmal pro Request (OncePerRequestFilter) und VOR den
 * eigentlichen Controllern. Ist kein/ein ungültiger Token vorhanden, wird
 * hier bewusst NICHTS abgelehnt - das überlässt der Filter der nach-
 * gelagerten Spring-Security-Autorisierung (SecurityConfig), die für
 * geschützte Endpunkte dann korrekt mit 401 antwortet. So bleiben
 * öffentliche Endpunkte (z.B. /api/auth/**) unabhängig von Token-Handling
 * erreichbar.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());

            try {
                String username = jwtService.extractUsername(token);

                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    // Nochmal explizit gegen die aktuelle DB-Signatur validieren
                    // (parseAndValidate prüft Signatur+Ablauf bereits in extractUsername,
                    // aber wir wollen sicherstellen, dass der User noch existiert/aktiv ist)
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (JwtException | org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                // Ungültiger/abgelaufener Token oder gelöschter User: einfach nicht
                // authentifizieren. Kein Stacktrace-Logging (könnte Tokens leaken),
                // nur ein Debug-Hinweis.
                log.debug("JWT-Validierung fehlgeschlagen: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
