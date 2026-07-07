package securemailbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Verschickt eine Benachrichtigung per E-Mail, WENN eine neue Nachricht
 * eingetroffen ist - bewusst OHNE den eigentlichen (Klartext-)Inhalt.
 *
 * Das ist dasselbe Prinzip, das auch echte sichere E-Mail-Dienste
 * (z.B. verschlüsselte Portale) verwenden: die Benachrichtigung geht
 * über normale, unverschlüsselte Kanäle (SMTP), aber der eigentliche
 * Inhalt bleibt nur im gesicherten System abrufbar. Ein Mitleser des
 * Mail-Verkehrs würde also nie die eigentliche Nachricht sehen.
 *
 * Fehler beim Mail-Versand werden bewusst NICHT weitergeworfen: das
 * Speichern der eigentlichen (verschlüsselten) Nachricht ist die
 * Kernfunktion und soll auch funktionieren, wenn z.B. der Mailserver
 * gerade nicht erreichbar ist. Das ist ein bewusster Trade-off
 * zwischen Konsistenz und Verfügbarkeit.
 */
@Service
public class NotificationMailService {

    private static final Logger log = LoggerFactory.getLogger(NotificationMailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public NotificationMailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@securemailbox.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendNewMessageNotification(String recipientUsername, String senderUsername, String subject) {
        try {
            // Vereinfachung für die Demo: aus dem Benutzernamen wird eine
            // lokale Fake-Adresse gebildet. In einem echten System gäbe es
            // eine richtige Adresse pro Benutzerkonto.
            String recipientAddress = recipientUsername + "@securemailbox.local";

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(recipientAddress);
            message.setSubject("Neue sichere Nachricht: " + subject);
            message.setText(
                    "Hallo " + recipientUsername + ",\n\n" +
                    senderUsername + " hat dir eine neue Nachricht über Secure Mailbox gesendet.\n\n" +
                    "Bitte logge dich in der Anwendung ein, um sie zu lesen.\n" +
                    "Aus Sicherheitsgründen wird der Inhalt niemals per E-Mail versendet.\n\n" +
                    "-- Secure Mailbox (Demo)"
            );

            mailSender.send(message);
            log.info("Benachrichtigungs-Mail an {} gesendet", recipientAddress);
        } catch (Exception e) {
            // Absichtlich nur loggen, nicht weiterwerfen - siehe Klassendoku
            log.warn("Benachrichtigungs-Mail an '{}' konnte nicht gesendet werden: {}",
                    recipientUsername, e.getMessage());
        }
    }
}
