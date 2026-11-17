package com.capdi.backend.domain.subscription;

import com.capdi.backend.domain.subscription.service.BillingKeyCipher;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class BillingKeyCipherTest {
    @Test void encryptsWithRandomIvAndBindsCiphertextToItsCustomer() {
        var cipher = new BillingKeyCipher(Base64.getEncoder().encodeToString(new byte[32]));
        String first = cipher.encrypt("test-billing-key", "customer-a");
        assertThat(first).isNotEqualTo(cipher.encrypt("test-billing-key", "customer-a"));
        assertThat(cipher.decrypt(first, "customer-a")).isEqualTo("test-billing-key");
        assertThatThrownBy(() -> cipher.decrypt(first, "customer-b")).isInstanceOf(IllegalStateException.class);
        String[] parts = first.split("\\.");
        byte[] ciphertext = Base64.getDecoder().decode(parts[2]); ciphertext[0] ^= 1;
        String modified = parts[0] + "." + parts[1] + "." + Base64.getEncoder().encodeToString(ciphertext);
        assertThatThrownBy(() -> cipher.decrypt(modified, "customer-a")).isInstanceOf(IllegalStateException.class);
    }

    @Test void noFallbackKeyIsInventedAndMalformedConfigurationFailsFast() {
        assertThatThrownBy(() -> new BillingKeyCipher("").encrypt("key", "customer")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new BillingKeyCipher("not-base64")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BillingKeyCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }
}
