package securemailbox.dto;

/**
 * Antwort auf Login/Register/Refresh. Der Refresh-Token selbst ist
 * NICHT Teil dieser Response - der wird als httpOnly-Cookie gesetzt und
 * ist damit für JavaScript im Browser gar nicht erst lesbar (schützt vor
 * Diebstahl per XSS). Nur der kurzlebige Access-Token geht an den Client,
 * der ihn im Speicher hält und im Authorization-Header mitschickt.
 */
public record AuthResponse(
        String accessToken,
        long expiresInSeconds,
        String username,
        String role
) {}
