package com.capdi.backend.domain.payment.gateway;

import com.capdi.backend.domain.payment.entity.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Adapter boundary, not the PaymentProvider persistence enum. Adapters supply verified observations; production execution remains disabled by default. */
public interface PaymentGateway {
    enum Capability { PREPARE, CONFIRM, LOOKUP, CANCEL, VERIFY_WEBHOOK }
    enum State { PREPARED, REQUESTED, WAITING_DEPOSIT, PAID, FAILED, CANCELED, UNKNOWN }
    record Identity(String code, String merchant, PaymentEnvironment environment) { }
    record Reference(Identity identity, String orderId, String attemptKey, String transactionId,
                     BigDecimal amount, String currency, PaymentMethod method) { }
    record Observation(Identity identity, String orderId, BigDecimal amount, String currency,
                       String transactionId, String rawStatus, State state, boolean authoritative) { }
    record VerifiedEvent(Identity identity, String eventId, String attemptKey, String orderId) { }

    Identity identity();
    boolean enabled();
    boolean supports(Capability capability);
    default Observation prepare(Reference reference) { throw new GatewayException(PaymentProcessingFailure.UNSUPPORTED); }
    // Browser reference is an opaque lookup/authorization reference, never proof of payment.
    default Observation confirm(Reference reference, String browserReference) { throw new GatewayException(PaymentProcessingFailure.UNSUPPORTED); }
    default Observation fetch(Reference reference) { throw new GatewayException(PaymentProcessingFailure.UNSUPPORTED); }
    default Observation cancel(Reference reference, String operationKey) { throw new GatewayException(PaymentProcessingFailure.UNSUPPORTED); }
    default VerifiedEvent verifyWebhook(Map<String, List<String>> headers, byte[] rawBody) {
        throw new GatewayException(PaymentProcessingFailure.UNSUPPORTED);
    }

    final class GatewayException extends RuntimeException {
        private final PaymentProcessingFailure failure;
        public GatewayException(PaymentProcessingFailure failure) { super("Payment gateway operation unavailable");this.failure=failure; }
        public PaymentProcessingFailure failure() { return failure; }
    }
}
