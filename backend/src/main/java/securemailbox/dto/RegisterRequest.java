package securemailbox.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(
                regexp = "^[a-zA-Z0-9_.-]+$",
                message = "Benutzername darf nur Buchstaben, Zahlen, '_', '.' und '-' enthalten"
        )
        String username,

        @NotBlank
        @Email
        String email,

        // Mindestlänge 12 statt der oft üblichen 8: NIST-Empfehlung (SP 800-63B)
        // setzt heute eher auf Länge als auf erzwungene Zeichenklassen-Mischung.
        // Auf eine Obergrenze/erzwungene Sonderzeichen wird bewusst verzichtet,
        // um keine Passphrasen ("KorrekteHausPferdBatterie") auszuschliessen.
        @NotBlank
        @Size(min = 12, max = 128)
        String password
) {}
