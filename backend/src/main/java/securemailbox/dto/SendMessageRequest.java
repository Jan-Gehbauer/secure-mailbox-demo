package securemailbox.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * WICHTIG: "sender" ist bewusst KEIN Feld mehr in diesem DTO.
 * Vorher konnte jeder Client per Request-Body behaupten, jemand anderes
 * zu sein ("sender": "ceo") - der Absender kommt jetzt ausschliesslich
 * aus dem authentifizierten Principal (siehe MessageController), der
 * durch das validierte JWT feststeht und vom Client nicht beeinflussbar ist.
 */
public record SendMessageRequest(
        @NotBlank String recipient,
        @NotBlank String subject,
        @NotBlank String body
) {}
