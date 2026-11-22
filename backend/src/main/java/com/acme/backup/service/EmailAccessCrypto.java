package com.acme.backup.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

@Component
public class EmailAccessCrypto {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final ObjectMapper mapper;
    private final String encodedKey;
    private final SecureRandom random = new SecureRandom();

    public EmailAccessCrypto(ObjectMapper mapper, @Value("${app.email-access.encryption-key:}") String encodedKey) {
        this.mapper = mapper;
        this.encodedKey = encodedKey;
    }

    public String encrypt(Map<String, String> values) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(mapper.writeValueAsBytes(values));
            byte[] output = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, output, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(output);
        } catch (Exception ex) {
            throw new IllegalStateException("Não foi possível criptografar o registro de acesso.", ex);
        }
    }

    public Map<String, String> decrypt(String payload) {
        try {
            byte[] input = Base64.getDecoder().decode(payload);
            if (input.length <= NONCE_BYTES) throw new IllegalArgumentException("Conteúdo criptografado inválido.");
            byte[] nonce = Arrays.copyOfRange(input, 0, NONCE_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(input, NONCE_BYTES, input.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            return mapper.readValue(cipher.doFinal(ciphertext), new TypeReference<>() { });
        } catch (Exception ex) {
            throw new IllegalStateException("Não foi possível descriptografar o registro de acesso.", ex);
        }
    }

    private SecretKeySpec key() {
        if (encodedKey.isBlank()) throw new IllegalStateException("Configure EMAIL_ACCESS_ENCRYPTION_KEY para habilitar o módulo.");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encodedKey); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("EMAIL_ACCESS_ENCRYPTION_KEY deve estar em Base64.", ex); }
        if (bytes.length != 32) throw new IllegalStateException("EMAIL_ACCESS_ENCRYPTION_KEY deve codificar exatamente 32 bytes.");
        return new SecretKeySpec(bytes, "AES");
    }
}
