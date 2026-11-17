package com.capdi.backend.domain.payment.gateway;
import java.math.BigDecimal;
/** Provider transport only. Internal approval and accounting belong to application services. */
public interface PayoutProvider {
 record Identity(String code,String environment) {}
 Identity identity();
 enum State { REQUESTED, UNKNOWN, SUCCEEDED, FAILED, CANCELED }
 record Request(String payoutIdentity,String recipientToken,BigDecimal amount,String currency) {
  @Override public String toString(){return "PayoutRequest[REDACTED]";}
 }
 record Result(String payoutIdentity,String providerPayoutId,BigDecimal amount,String currency,State state,boolean authoritative) {}
 boolean enabled();
 Result request(Request request);
 Result lookup(String payoutIdentity,String providerPayoutId);
}
