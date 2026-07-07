package securemailbox.dto;

import securemailbox.entity.EncryptedMessage;

import java.time.Instant;

public record MessageResponse(
        Long id,
        String sender,
        String recipient,
        String subject,
        String body,
        Instant createdAt
) {
    public static MessageResponse of(EncryptedMessage entity, String decryptedBody) {
        return new MessageResponse(
                entity.getId(),
                entity.getSender(),
                entity.getRecipient(),
                entity.getSubject(),
                decryptedBody,
                entity.getCreatedAt()
        );
    }
}
