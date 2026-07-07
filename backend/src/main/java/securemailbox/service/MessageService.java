package securemailbox.service;

import securemailbox.dto.MessageResponse;
import securemailbox.dto.SendMessageRequest;
import securemailbox.entity.EncryptedMessage;
import securemailbox.repository.MessageRepository;
import securemailbox.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MessageService {

    private final MessageRepository repository;
    private final EncryptionService encryptionService;
    private final NotificationMailService notificationMailService;
    private final UserRepository userRepository;

    public MessageService(
            MessageRepository repository,
            EncryptionService encryptionService,
            NotificationMailService notificationMailService,
            UserRepository userRepository) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.notificationMailService = notificationMailService;
        this.userRepository = userRepository;
    }

    /**
     * @param authenticatedSender Benutzername aus dem validierten JWT
     *                            (SecurityContext), NICHT aus dem Request-Body.
     *                            Das ist der Kern des IDOR-Fixes: der Client kann
     *                            nicht mehr behaupten, jemand anderes zu sein.
     */
    public MessageResponse send(SendMessageRequest request, String authenticatedSender) {
        if (!userRepository.existsByUsername(request.recipient())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Empfänger '" + request.recipient() + "' existiert nicht");
        }

        EncryptionService.EncryptedPayload payload = encryptionService.encrypt(request.body());

        EncryptedMessage entity = new EncryptedMessage();
        entity.setSender(authenticatedSender);
        entity.setRecipient(request.recipient());
        entity.setSubject(request.subject());
        entity.setCiphertext(payload.ciphertextBase64());
        entity.setIv(payload.ivBase64());

        EncryptedMessage saved = repository.save(entity);

        // Benachrichtigung per Mail - läuft bewusst NACH dem erfolgreichen
        // Speichern und wirft bei Fehlern keine Exception (siehe
        // NotificationMailService), damit ein Mail-Problem niemals das
        // eigentliche Senden/Speichern der Nachricht verhindert.
        notificationMailService.sendNewMessageNotification(
                request.recipient(), authenticatedSender, request.subject());

        // Klartext geben wir nur in der API-Response direkt nach dem Senden zurück,
        // damit der Aufrufer eine Bestätigung sieht - nicht weil wir ihn zwischenspeichern.
        return MessageResponse.of(saved, request.body());
    }

    public List<MessageResponse> getInbox(String recipient) {
        return repository.findByRecipientOrderByCreatedAtDesc(recipient).stream()
                .map(this::decrypt)
                .toList();
    }

    public List<MessageResponse> getSent(String sender) {
        return repository.findBySenderOrderByCreatedAtDesc(sender).stream()
                .map(this::decrypt)
                .toList();
    }

    private MessageResponse decrypt(EncryptedMessage entity) {
        String plaintext = encryptionService.decrypt(entity.getCiphertext(), entity.getIv());
        return MessageResponse.of(entity, plaintext);
    }
}
