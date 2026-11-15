package com.capdi.backend.domain.payment.gateway.portone;

import java.time.Duration;

/** No generated toString: secrets must never be serialized or logged. */
public final class PortOneSettings {
    private final boolean enabled;
    private final String merchantId, storeId, channelKey, apiSecret, webhookSecret;
    private final Duration timeout;
    public PortOneSettings(boolean enabled, boolean sandboxApproved, String environment,
            String merchantId, String storeId, String channelKey, String apiSecret,
            String webhookSecret, Duration timeout) {
        this.enabled=enabled;
        this.merchantId=merchantId;this.storeId=storeId;this.channelKey=channelKey;
        this.apiSecret=apiSecret;this.webhookSecret=webhookSecret;this.timeout=timeout;
        if(enabled && (!sandboxApproved || !"SANDBOX".equals(environment)
                || !identifier(merchantId) || !identifier(storeId) || !identifier(channelKey)
                || apiSecret==null || apiSecret.isBlank() || apiSecret.length()>4096
                || apiSecret.chars().anyMatch(c->c<33 || c>126)
                || timeout==null || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofSeconds(30))>0))
            throw new IllegalArgumentException("PortOne sandbox configuration invalid");
    }
    public static boolean identifier(String s) {return s!=null && s.matches("[A-Za-z0-9_-]{1,100}");}
    public boolean enabled(){return enabled;}
    public String merchantId(){return merchantId;}
    public String storeId(){return storeId;}
    public String channelKey(){return channelKey;}
    String apiSecret(){return apiSecret;}
    String webhookSecret(){return webhookSecret;}
    public Duration timeout(){return timeout;}
    @Override public String toString(){return "PortOneSettings[REDACTED]";}
}
