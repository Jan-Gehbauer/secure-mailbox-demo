package securemailbox.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationMailServiceTest {

    private JavaMailSender mailSender;
    private NotificationMailService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        service = new NotificationMailService(mailSender, "no-reply@securemailbox.local");
    }

    @Test
    void sendNewMessageNotification_sendsMailWithoutPlaintextBody() {
        service.sendNewMessageNotification("alice", "bob", "Wichtiges Thema");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();
        assertThat(sentMessage.getTo()).contains("alice@securemailbox.local");
        assertThat(sentMessage.getSubject()).contains("Wichtiges Thema");
        // Wichtig: der eigentliche Nachrichteninhalt (Body) darf NIE Teil der
        // Benachrichtigung sein - nur der Betreff und ein Hinweis aufs Einloggen.
        assertThat(sentMessage.getText()).contains("logge dich");
    }

    @Test
    void sendNewMessageNotification_doesNotThrow_whenMailSendingFails() {
        doThrow(new MailSendException("SMTP nicht erreichbar"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // Darf keine Exception werfen - das Speichern der eigentlichen
        // Nachricht soll nie an einem Mail-Server-Problem scheitern.
        service.sendNewMessageNotification("alice", "bob", "Betreff");
    }
}
