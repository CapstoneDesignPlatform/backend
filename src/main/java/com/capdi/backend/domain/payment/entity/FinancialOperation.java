package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.domain.payment.gateway.FinancialGateway;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="financial_operations")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class FinancialOperation extends BaseTimeEntity {
    public enum State { READY, REQUESTED, PENDING, SUCCEEDED, FAILED, UNKNOWN, BLOCKED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=40,unique=true,updatable=false) private String reference;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private FinancialGateway.Kind kind;
    @Column(name="provider_code",nullable=false,length=40,updatable=false) private String providerCode;
    @Column(name="provider_merchant_id",length=100,updatable=false) private String providerMerchantId;
    @Column(name="provider_store_id",nullable=false,length=100,updatable=false) private String providerStoreId;
    @Column(name="provider_account_id",nullable=false,length=200,updatable=false) private String providerAccountId;
    @Column(name="provider_environment",nullable=false,length=20,updatable=false) private String providerEnvironment;
    @Column(name="payment_id",updatable=false) private Long paymentId;
    @Column(name="refund_request_id",updatable=false) private Long refundRequestId;
    @Column(name="seller_draft_id",updatable=false) private Long sellerDraftId;
    @Column(name="account_id",updatable=false) private Long accountId;
    @Column(name="approval_id",updatable=false) private Long approvalId;
    @Column(name="invoice_id",updatable=false) private Long invoiceId;
    @Column(name="source_version",nullable=false,updatable=false) private long sourceVersion;
    @Column(name="key_hash",nullable=false,length=64,updatable=false) private String keyHash;
    @Column(name="request_hash",nullable=false,length=64,updatable=false) private String requestHash;
    @Lob @Column(name="encrypted_payload",nullable=false,columnDefinition="LONGTEXT",updatable=false) private String encryptedPayload;
    @Column(name="external_id",length=200) private String externalId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State state;
    @Enumerated(EnumType.STRING) @Column(name="nts_state",nullable=false,length=20) private FinancialGateway.NtsState ntsState;
    @Column(name="execution_token",length=36) private String executionToken;
    @Column(name="lease_until") private Instant leaseUntil;
    @Column(name="next_lookup_at",nullable=false) private Instant nextLookupAt;
    @Column(name="submit_claimed",nullable=false) private boolean submitClaimed;
    @Column(name="lookup_count",nullable=false) private int lookupCount;
    @Column(name="failure_code",nullable=false,length=60) private String failureCode;
    @Version @Column(nullable=false) private long version;
    public static FinancialOperation create(String reference,FinancialGateway.Kind kind,FinancialGateway.Identity identity,Long payment,Long refund,Long seller,Long account,
            Long approval,Long invoice,long sourceVersion,String key,String hash,String payload,Instant now) {
        var o=new FinancialOperation();o.reference=reference;o.kind=kind;o.providerCode=identity.code();o.providerMerchantId=identity.merchant();o.providerStoreId=identity.store();o.providerAccountId=identity.providerAccountId();o.providerEnvironment=identity.environment();o.paymentId=payment;
        o.refundRequestId=refund;o.sellerDraftId=seller;o.accountId=account;o.approvalId=approval;o.invoiceId=invoice;
        o.sourceVersion=sourceVersion;o.keyHash=key;o.requestHash=hash;o.encryptedPayload=payload;
        o.state=State.READY;o.ntsState=FinancialGateway.NtsState.NOT_SENT;o.failureCode="NONE";o.nextLookupAt=now;
        return o;
    }
    public boolean busy(Instant now){return leaseUntil!=null && leaseUntil.isAfter(now);}
    public String claim(boolean lookup,Instant now) {
        if(busy(now) || state==State.FAILED || state==State.BLOCKED
                || state==State.SUCCEEDED && kind!=FinancialGateway.Kind.TAX_INVOICE && kind!=FinancialGateway.Kind.PARTNER)return null;
        if(!lookup && state!=State.READY)return null;
        if(lookup && (!submitClaimed || state==State.READY))return null; // never send a write from a recovery worker.
        executionToken=UUID.randomUUID().toString();leaseUntil=now.plusSeconds(45);
        if(!lookup){submitClaimed=true;state=State.REQUESTED;}
        else lookupCount++;
        return executionToken;
    }
    public boolean owns(String token){return token!=null && token.equals(executionToken);}
    public void observed(FinancialGateway.Evidence e,Instant now) {
        if(externalId!=null && e.externalId()!=null && !externalId.equals(e.externalId()))throw new IllegalStateException("External identity mismatch");
        if(e.externalId()!=null)externalId=e.externalId();
        state=switch(e.state()){case PENDING->State.PENDING;case SUCCEEDED->State.SUCCEEDED;case FAILED->State.FAILED;case UNKNOWN->State.UNKNOWN;};
        ntsState=e.ntsState()==null?FinancialGateway.NtsState.UNKNOWN:e.ntsState();
        failureCode=state==State.UNKNOWN?"UNCONFIRMED":"NONE";release(now);
        if(lookupCount>=8 && state!=State.SUCCEEDED && state!=State.FAILED){state=State.BLOCKED;failureCode="LOOKUP_LIMIT";}
    }
    public void uncertain(String code,Instant now){state=State.UNKNOWN;failureCode=code;release(now);if(lookupCount>=8){state=State.BLOCKED;failureCode="LOOKUP_LIMIT";}}
    public void block(String code,Instant now){state=State.BLOCKED;failureCode=code;release(now);}
    private void release(Instant now){executionToken=null;leaseUntil=null;nextLookupAt=now.plusSeconds(Math.min(3600,30L*(1L<<Math.min(lookupCount,7))));}
    public void manualReview(Instant now){if(submitClaimed && state==State.BLOCKED && !java.util.Set.of("STALE_APPROVAL","MANUAL_HOLD","REFUND_ADJUSTMENT").contains(failureCode)){state=State.UNKNOWN;lookupCount=0;nextLookupAt=now;}}
    public FinancialGateway.Identity identity(){return new FinancialGateway.Identity(providerCode,providerMerchantId,providerStoreId,providerEnvironment,providerAccountId);}
}
