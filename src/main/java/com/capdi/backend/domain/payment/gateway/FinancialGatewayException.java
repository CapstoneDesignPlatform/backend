package com.capdi.backend.domain.payment.gateway;
public final class FinancialGatewayException extends RuntimeException {
 public enum Failure { DISABLED, UNSUPPORTED, TIMEOUT, NETWORK, PROVIDER_ERROR, RESPONSE_MISMATCH }
 private final Failure failure;
 public FinancialGatewayException(Failure failure){super("Financial provider operation unavailable");this.failure=failure;}
 public Failure failure(){return failure;}
}
