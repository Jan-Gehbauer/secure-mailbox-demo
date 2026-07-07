package securemailbox.service;

import securemailbox.dto.MessageResponse;
import securemailbox.dto.SendMessageRequest;
import securemailbox.entity.EncryptedMessage;
import securemailbox.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Prüft insbesondere die sicherheitskritische Eigenschaft, dass der
 * Absender einer Nachricht ausschliesslich aus dem authentifizierten
 * Principal kommt (Parameter "authenticatedSender"), nicht aus dem
 * Request-Body - das ist der Kern des IDOR-Fixes in MessageController.
 */
class MessageServiceTest {

    private MessageRepository repository;
    private EncryptionService encryptionService;
    private NotificationMailService notificationMailService;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        repository = mock(MessageRepository.class);
        encryptionService = mock(EncryptionService.class);
        notificationMailService = mock(NotificationMailService.class);
        messageService = new MessageService(repository, encryptionService, notificationMailService);
    }

    @Test
    void send_encryptsBodyAndPersistsWithAuthenticatedSender() {
        SendMessageRequest request = new SendMessageRequest("bob", "Betreff", "Geheime Nachricht");

        var payload = new EncryptionService.EncryptedPayload("ciphertext-base64", "iv-base64");
        when(encryptionService.encrypt("Geheime Nachricht")).thenReturn(payload);

        when(repository.save(any(EncryptedMessage.class))).thenAnswer(invocation -> {
            EncryptedMessage entity = invocation.getArgument(0);
            entity.setId(42L);
            entity.setCreatedAt(Instant.now());
            return entity;
        });

        MessageResponse response = messageService.send(request, "alice");

        ArgumentCaptor<EncryptedMessage> captor = ArgumentCaptor.forClass(EncryptedMessage.class);
        verify(repository).save(captor.capture());
        EncryptedMessage saved = captor.getValue();

        // "alice" kommt aus dem zweiten Methodenparameter (Authentication),
        // NICHT aus request.recipient()/request.subject()/request.body()
        assertThat(saved.getSender()).isEqualTo("alice");
        assertThat(saved.getRecipient()).isEqualTo("bob");
        assertThat(saved.getCiphertext()).isEqualTo("ciphertext-base64");
        assertThat(saved.getIv()).isEqualTo("iv-base64");

        // Klartext in der Response nur als direkte Sende-Bestätigung,
        // nicht aus der DB nachgeladen
        assertThat(response.body()).isEqualTo("Geheime Nachricht");
        assertThat(response.sender()).isEqualTo("alice");
    }

    @Test
    void send_triggersNotificationMail_withCorrectRecipientSenderSubject() {
        SendMessageRequest request = new SendMessageRequest("bob", "Wichtig", "Inhalt");
        when(encryptionService.encrypt(any())).thenReturn(new EncryptionService.EncryptedPayload("c", "i"));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        messageService.send(request, "alice");

        verify(notificationMailService).sendNewMessageNotification("bob", "alice", "Wichtig");
    }

    @Test
    void getInbox_decryptsEachStoredMessage() {
        EncryptedMessage stored = new EncryptedMessage();
        stored.setId(1L);
        stored.setSender("alice");
        stored.setRecipient("bob");
        stored.setSubject("Betreff");
        stored.setCiphertext("c");
        stored.setIv("i");
        stored.setCreatedAt(Instant.now());

        when(repository.findByRecipientOrderByCreatedAtDesc("bob")).thenReturn(List.of(stored));
        when(encryptionService.decrypt("c", "i")).thenReturn("Entschlüsselter Text");

        List<MessageResponse> inbox = messageService.getInbox("bob");

        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).body()).isEqualTo("Entschlüsselter Text");
        assertThat(inbox.get(0).sender()).isEqualTo("alice");
    }

    @Test
    void getSent_queriesBySenderOnly_notByRecipient() {
        when(repository.findBySenderOrderByCreatedAtDesc("alice")).thenReturn(List.of());

        List<MessageResponse> sent = messageService.getSent("alice");

        assertThat(sent).isEmpty();
        verify(repository).findBySenderOrderByCreatedAtDesc("alice");
        verify(repository, never()).findByRecipientOrderByCreatedAtDesc(any());
    }
}
