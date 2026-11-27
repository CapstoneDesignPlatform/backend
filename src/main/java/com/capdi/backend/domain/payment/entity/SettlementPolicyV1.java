package com.capdi.backend.domain.payment.entity;

import java.math.BigDecimal;

/** Temporary internal allocation policy. PG fees are charged to the platform, never deducted from expert share. */
public final class SettlementPolicyV1 {
    public static final String VERSION = "BASE_ALLOCATION_10_90_V1";
    public static final BigDecimal PLATFORM_RATE = new BigDecimal("0.1000");
    public static final BigDecimal EXPERT_RATE = new BigDecimal("0.9000");

    private SettlementPolicyV1() {}

    public static BigDecimal platformAmount(BigDecimal gross) {
        return gross.multiply(PLATFORM_RATE).setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    public static BigDecimal expertAmount(BigDecimal gross) {
        return gross.subtract(platformAmount(gross));
    }
}
