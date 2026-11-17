package com.capdi.backend.domain.subscription.gateway;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * All methods run outside DB transactions. Implementations must authenticate the merchant,
 * bind registration proof to the expected customer, use bounded timeouts, and never log credentials.
 * Charge must use orderId as a stable provider idempotency key. Lookup must never create a charge.
 * Only a verified, definitive decline may be returned as FAILED; timeout/not-found is UNKNOWN.
 */
public interface SubscriptionPgClient {
    String provider();
    boolean available();
    Registration register(String registrationToken, String expectedCustomerRef, String registrationId);
    Registration findRegistration(String expectedCustomerRef, String registrationId);
    Charge charge(String billingKey, String customerRef, String orderId, BigDecimal amount, String currency);
    Charge findCharge(String customerRef, String orderId);

    enum Outcome { SUCCEEDED, FAILED, UNKNOWN }

    record Registration(Outcome outcome, String customerRef, String billingKey) {
        @Override public String toString() { return "Registration[outcome=" + outcome + "]"; }
    }

    record Charge(Outcome outcome, String customerRef, String orderId, String paymentId,
                  BigDecimal amount, String currency, Instant approvedAt) { }
}
