package com.capdi.backend.domain.payment.gateway;

/** Stable CAPDI-side provider account partition, distinct from PortOne merchantId. */
public final class ProviderAccountIdentity {
    private ProviderAccountIdentity() { }
    public static String store(String providerCode, String storeId) {
        if (!valid(providerCode) || !valid(storeId)) throw new IllegalArgumentException("Provider account identity invalid");
        return "provider:" + providerCode + ":store:" + storeId;
    }
    /** Historical rows retain their merchant_id meaning and get an explicitly legacy namespace. */
    public static String legacyMerchant(String providerCode, String merchantId) {
        if (!valid(providerCode) || !valid(merchantId)) throw new IllegalArgumentException("Provider account identity invalid");
        return "provider:" + providerCode + ":legacy-merchant:" + merchantId;
    }
    private static boolean valid(String value) {
        return value != null && value.matches("[A-Za-z0-9_.:-]{1,120}");
    }
}
