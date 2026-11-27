package com.capdi.backend.domain.payment.gateway.portone;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;

/** No generated toString: secrets must never be serialized or logged. */
public final class PortOneSettings {
    private final boolean enabled;
    private final String merchantId, storeId, channelKey, apiSecret, webhookSecret;
    private final Duration timeout;
    public PortOneSettings(boolean enabled, boolean sandboxApproved, String environment,
            String merchantId, String storeId, String channelKey, String apiSecret,
            String webhookSecret, Duration timeout) {
        this.enabled=enabled;
        this.merchantId=merchantId==null || merchantId.isBlank() ? null : merchantId;
        this.storeId=storeId;this.channelKey=channelKey;
        this.apiSecret=apiSecret;this.webhookSecret=webhookSecret;this.timeout=timeout;
        if(enabled) {
            String issue = !sandboxApproved ? "SANDBOX_APPROVAL_REQUIRED"
                    : !"SANDBOX".equals(environment) ? "ENVIRONMENT_NOT_SANDBOX"
                    : this.merchantId != null && !identifier(this.merchantId) ? "MERCHANT_ID_INVALID"
                    : !identifier(storeId) ? "STORE_ID_INVALID"
                    : !identifier(channelKey) ? "CHANNEL_KEY_INVALID"
                    : apiSecret==null || apiSecret.isBlank() || apiSecret.length()>4096
                            || apiSecret.chars().anyMatch(c->c<33 || c>126) ? "API_SECRET_INVALID"
                    : timeout==null || timeout.isNegative() || timeout.isZero()
                            || timeout.compareTo(Duration.ofSeconds(30))>0 ? "TIMEOUT_INVALID"
                    : null;
            if(issue!=null)throw new IllegalArgumentException("PortOne sandbox configuration invalid: "+issue);
        }
    }
    public static boolean identifier(String s) {return s!=null && s.matches("[A-Za-z0-9_-]{1,100}");}
    /** Opt-in receiver must never start with an unavailable provider or unusable signing key. */
    public void validateWebhookConfiguration(boolean webhookEnabled) {
        if(!webhookEnabled)return;
        byte[] key=null;
        try {
            if(!enabled || webhookSecret==null || webhookSecret.isBlank() || webhookSecret.length()>128)
                throw new IllegalArgumentException();
            String encoded=webhookSecret.startsWith("whsec_")?webhookSecret.substring(6):webhookSecret;
            key=Base64.getDecoder().decode(encoded);
            if(key.length<24 || key.length>64)throw new IllegalArgumentException();
        } catch(IllegalArgumentException ignored) {
            // Deliberately omit the decoder exception/cause and all supplied configuration values.
            throw new IllegalArgumentException("PortOne webhook configuration invalid");
        } finally {
            if(key!=null)Arrays.fill(key,(byte)0);
        }
    }
    public boolean enabled(){return enabled;}
    public String merchantId(){return merchantId;}
    public String storeId(){return storeId;}
    public String channelKey(){return channelKey;}
    String apiSecret(){return apiSecret;}
    String webhookSecret(){return webhookSecret;}
    public Duration timeout(){return timeout;}
    @Override public String toString(){return "PortOneSettings[REDACTED]";}
}
