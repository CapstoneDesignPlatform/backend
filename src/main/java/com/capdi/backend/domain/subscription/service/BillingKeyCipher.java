package com.capdi.backend.domain.subscription.service;

import com.capdi.backend.domain.subscription.exception.SubscriptionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class BillingKeyCipher {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public BillingKeyCipher(@Value("${subscription.billing-key-encryption-key:}") String encoded) {
        if (encoded.isBlank()) { key = null; return; }
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException ignored) { throw new IllegalStateException("Invalid subscription encryption key configuration"); }
        if (decoded.length != 32) throw new IllegalStateException("Subscription encryption requires a 32-byte key");
        key = decoded;
    }

    public void requireConfigured() { if (key == null) throw SubscriptionException.unavailable(); }

    public String encrypt(String value, String customerRef) {
        requireConfigured();
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, customerRef);
            return "v1." + Base64.getEncoder().encodeToString(iv) + "."
                    + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) { throw new IllegalStateException("Could not protect billing credential"); }
    }

    public String decrypt(String value, String customerRef) {
        requireConfigured();
        try {
            String[] parts = value.split("\\.");
            if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            if (iv.length != 12) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, iv, customerRef)
                    .doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception ignored) { throw new IllegalStateException("Could not read billing credential"); }
    }

    private Cipher cipher(int mode, byte[] iv, String customerRef) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(customerRef.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
