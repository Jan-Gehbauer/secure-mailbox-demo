package securemailbox.repository;

import securemailbox.entity.EncryptedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<EncryptedMessage, Long> {

    List<EncryptedMessage> findByRecipientOrderByCreatedAtDesc(String recipient);

    List<EncryptedMessage> findBySenderOrderByCreatedAtDesc(String sender);
}
