package securemailbox.controller;

import securemailbox.dto.MessageResponse;
import securemailbox.dto.SendMessageRequest;
import securemailbox.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CORS läuft zentral über SecurityConfig (CorsConfigurationSource) statt
 * per @CrossOrigin hier - ein Ort für die Origin-Whitelist statt verstreut
 * über Controller.
 *
 * SICHERHEITSKRITISCH: Alle drei Endpunkte lesen die Benutzeridentität
 * ausschliesslich aus dem `Authentication`-Objekt, das Spring Security aus
 * dem validierten JWT befüllt (siehe JwtAuthenticationFilter) - NIE aus
 * einem Pfad-Parameter oder Request-Body-Feld. Vorher konnte jeder
 * Aufrufer per GET /api/messages/inbox/{recipient} die Inbox JEDES
 * beliebigen Users abrufen (IDOR / Broken Object Level Authorization,
 * OWASP API Security Top 10 #1). Jetzt gibt es dafür gar keinen
 * Pfad-Parameter mehr - "wessen Inbox" ergibt sich zwingend aus dem
 * eigenen, validierten Token.
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@Valid @RequestBody SendMessageRequest request, Authentication authentication) {
        return messageService.send(request, authentication.getName());
    }

    @GetMapping("/inbox")
    public List<MessageResponse> inbox(Authentication authentication) {
        return messageService.getInbox(authentication.getName());
    }

    @GetMapping("/sent")
    public List<MessageResponse> sent(Authentication authentication) {
        return messageService.getSent(authentication.getName());
    }
}
