package securemailbox.config;

import securemailbox.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Zentrale Security-Konfiguration.
 *
 * Design-Entscheidungen:
 * - STATELESS Sessions: keine serverseitige HttpSession, jeder Request
 *   trägt seine Authentifizierung selbst (Access-Token im Header). Das
 *   ist die Grundvoraussetzung dafür, dass das Backend horizontal
 *   skaliert (siehe Jobbeschreibung: "Skalierung zum Mass-Service") ohne
 *   Sticky-Sessions oder geteilten Session-Store.
 * - CSRF ist deaktiviert, weil wir keine Cookie-basierte Session-
 *   Authentifizierung für die eigentlichen API-Calls nutzen (die sind
 *   Bearer-Token-basiert, die immun gegen klassisches CSRF sind, weil
 *   ein fremdes Formular den Header nicht setzen kann). Das Refresh-
 *   Token-Cookie selbst ist zusätzlich mit SameSite=Strict abgesichert.
 * - CORS: explizite Konfiguration statt @CrossOrigin auf Controllern -
 *   ein Ort, an dem man sieht/ändert, welche Origins vertraut werden.
 *   allowCredentials=true wird gebraucht, damit der Browser das
 *   Refresh-Token-Cookie bei Cross-Origin-Requests (Angular Dev-Server
 *   auf einem anderen Port) überhaupt mitschickt.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                // Exceptions aus der Filterkette (z.B. "kein/ungültiger Token")
                // laufen NICHT durch den @RestControllerAdvice der Controller-Ebene,
                // deshalb hier explizit als JSON statt Spring-Security-Standardseite.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::handleUnauthorized)
                        .accessDeniedHandler(this::handleForbidden)
                )
                // Nur relevant für die H2-Konsole (Dev-Only): die rendert sich in
                // einem Frame, was Spring Security standardmässig per X-Frame-Options
                // unterbindet.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void handleUnauthorized(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        writeJsonError(response, HttpStatus.UNAUTHORIZED, "Nicht authentifiziert");
    }

    private void handleForbidden(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        writeJsonError(response, HttpStatus.FORBIDDEN, "Zugriff verweigert");
    }

    private void writeJsonError(
            HttpServletResponse response,
            HttpStatus status,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                Instant.now(), status.value(), status.getReasonPhrase(), message));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt mit Standard-Strength (10) - guter Kompromiss aus Sicherheit
        // und Login-Latenz. Bei Bedarf über den Konstruktor-Parameter erhöhbar.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }
}
