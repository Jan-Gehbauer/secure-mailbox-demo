package securemailbox.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() throws Exception {
        // Für den Test generieren wir einen frischen Key, unabhängig von application.properties
        String key = EncryptionService.generateNewMasterKeyBase64();
        encryptionService = new EncryptionService(key);
    }

    @Test
    void encryptThenDecrypt_returnsOriginalPlaintext() {
        String original = "Dies ist eine geheime Nachricht an PrivaSphere.";

        EncryptionService.EncryptedPayload payload = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(payload.ciphertextBase64(), payload.ivBase64());

        assertEquals(original, decrypted);
    }

    @Test
    void encrypt_producesDifferentCiphertextForSamePlaintext() {
        // Wichtige Eigenschaft von GCM: gleicher Klartext -> unterschiedlicher
        // Chiffretext, weil jedes Mal ein neuer IV verwendet wird.
        String plaintext = "Immer gleicher Text";

        EncryptionService.EncryptedPayload first = encryptionService.encrypt(plaintext);
        EncryptionService.EncryptedPayload second = encryptionService.encrypt(plaintext);

        assertNotEquals(first.ciphertextBase64(), second.ciphertextBase64());
        assertNotEquals(first.ivBase64(), second.ivBase64());
    }

    @Test
    void decrypt_withTamperedCiphertext_throwsException() {
        // GCM erkennt Manipulation dank Auth-Tag und wirft eine Exception,
        // statt stillschweigend falschen/kaputten Klartext zu liefern.
        EncryptionService.EncryptedPayload payload = encryptionService.encrypt("Original");

        String tampered = payload.ciphertextBase64().substring(0, payload.ciphertextBase64().length() - 4) + "abcd";

        assertThrows(EncryptionService.EncryptionException.class,
                () -> encryptionService.decrypt(tampered, payload.ivBase64()));
    }

    @Test
    void decrypt_withWrongKey_throwsException() throws Exception {
        EncryptionService.EncryptedPayload payload = encryptionService.encrypt("Geheim");

        EncryptionService otherService = new EncryptionService(
                EncryptionService.generateNewMasterKeyBase64());

        assertThrows(EncryptionService.EncryptionException.class,
                () -> otherService.decrypt(payload.ciphertextBase64(), payload.ivBase64()));
    }
}
