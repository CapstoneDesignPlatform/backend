package com.capdi.backend.domain.payment.gateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/** Financial writes are explicit; reconciliation is always lookup-only. */
public interface FinancialGateway {
    enum Kind { REFUND, PARTNER, PAYOUT, TAX_INVOICE }
    record Identity(String code,String merchant,String store,String environment) {}
    Identity identity();
    enum State { PENDING, SUCCEEDED, FAILED, UNKNOWN }
    enum BankState { UNVERIFIED, VERIFIED, REJECTED, UNKNOWN }
    enum NtsState { NOT_SENT, PENDING, SUCCEEDED, FAILED, UNKNOWN }
    record Command(String reference,Kind kind,JsonNode payload) {
        @Override public String toString(){return "FinancialCommand[REDACTED]";}
    }
    record Evidence(String reference,String externalId,State state,BigDecimal amount,String currency,
                    boolean accountVerified,boolean kycVerified,NtsState ntsState,BankState bankState) {
        public Evidence(String reference,String externalId,State state,BigDecimal amount,String currency,
                boolean accountVerified,boolean kycVerified,NtsState ntsState) {
            this(reference,externalId,state,amount,currency,accountVerified,kycVerified,ntsState,
                accountVerified?BankState.VERIFIED:BankState.UNKNOWN);
        }
    }
    boolean canSubmit(Kind kind);
    boolean canLookup(Kind kind);
    Evidence submit(Command command);
    Evidence lookup(Command command,String externalId);
}
