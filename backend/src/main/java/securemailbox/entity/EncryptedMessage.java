package securemailbox.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Repräsentiert eine Nachricht, wie sie in der DB liegt: nur Chiffretext,
 * nie Klartext. IV und Auth-Tag werden zusammen mit dem Chiffretext
 * gespeichert (AES-GCM liefert beides im selben Byte-Array zurück).
 *
 * Getter/Setter hier bewusst manuell statt via Lombok, um eine
 * IDE/Compiler-Fehlerquelle zu vermeiden.
 */
@Entity
@Table(name = "messages")
public class EncryptedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    // Base64-kodierter Chiffretext (enthält bei GCM bereits das Auth-Tag).
    // Bewusst columnDefinition="TEXT" statt @Lob: @Lob würde Hibernate bei
    // Postgres dazu bringen, einen Large Object (OID) zu verwenden, der nur
    // innerhalb einer Transaktion gelesen werden kann - unsere Read-Endpoints
    // laufen aber ohne @Transactional im Auto-Commit-Modus. TEXT ist hier
    // ausreichend und deutlich unkomplizierter.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String ciphertext;

    // Base64-kodierter Initialisierungsvektor, pro Nachricht neu generiert
    @Column(nullable = false)
    private String iv;

    @Column(nullable = false)
    private Instant createdAt;

    public EncryptedMessage() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
