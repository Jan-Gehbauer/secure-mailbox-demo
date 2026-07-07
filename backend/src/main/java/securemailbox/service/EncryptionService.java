package securemailbox.service;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;

/**
 * Kapselt symmetrische Verschlüsselung mit AES-256 im GCM-Modus
 * (authenticated encryption: schützt gleichzeitig Vertraulichkeit
 * und Integrität).
 *
 * BouncyCastle wird explizit als Provider registriert statt der
 * Standard-JVM-Implementierung zu vertrauen - in der Praxis meist,
 * weil man zusätzliche Algorithmen (z.B. bestimmte elliptische
 * Kurven) oder konsistentes Verhalten über JVM-Versionen hinweg
 * braucht.
 *
 * WICHTIG (nur zur Transparenz, für die Doku/README):
 * Der Master-Key liegt hier der Einfachheit halber in
 * application.properties. In einem echten System gehört er in
 * einen Key-Vault / HSM, niemals ins Repository oder Klartext-Config.
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12; // empfohlene Länge für GCM

    private final SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    static {
        // Provider einmalig registrieren
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public EncryptionService(@Value("${app.encryption.master-key-base64}") String masterKeyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "Master key muss 32 Bytes (AES-256) lang sein, war: " + keyBytes.length);
        }
        this.masterKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public record EncryptedPayload(String ciphertextBase64, String ivBase64) {}

    public EncryptedPayload encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            return new EncryptedPayload(
                    Base64.getEncoder().encodeToString(ciphertext),
                    Base64.getEncoder().encodeToString(iv)
            );
        } catch (Exception e) {
            throw new EncryptionException("Verschlüsselung fehlgeschlagen", e);
        }
    }

    public String decrypt(String ciphertextBase64, String ivBase64) {
        try {
            byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
            byte[] iv = Base64.getDecoder().decode(ivBase64);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, "UTF-8");
        } catch (Exception e) {
            throw new EncryptionException("Entschlüsselung fehlgeschlagen", e);
        }
    }

    /**
     * Hilfsmethode, nur für lokale Entwicklung: generiert einen neuen
     * Base64-kodierten 256-bit Schlüssel, den man in application.properties
     * eintragen kann. Nicht Teil der eigentlichen Anwendung.
     */
    public static String generateNewMasterKeyBase64() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(256);
        SecretKey key = keyGen.generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
