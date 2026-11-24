package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.Duration;
import java.util.Objects;

@Entity @Table(name="payment_webhook_events",uniqueConstraints=@UniqueConstraint(name="uk_payment_event_account_identity",
        columnNames={"provider_code","provider_account_id","environment","event_key_hash"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PaymentWebhookEvent extends BaseTimeEntity {
    public enum State { RECEIVED, PROCESSING, PROCESSED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="attempt_id",nullable=false,updatable=false) private Long attemptId;
    @Column(name="provider_code",nullable=false,length=40,updatable=false) private String providerCode;
    @Column(name="merchant_id",length=100,updatable=false) private String merchantId;
    @Column(name="provider_account_id",nullable=false,length=200,updatable=false) private String providerAccountId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private PaymentEnvironment environment;
    @Column(name="event_key_hash",nullable=false,length=64,updatable=false) private String eventKeyHash;
    @Column(name="payload_hash",nullable=false,length=64,updatable=false) private String payloadHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State state;
    @Column(name="processing_token",length=36) private String processingToken;
    @Column(name="lease_until") private Instant leaseUntil;
    @Column(name="attempt_count",nullable=false) private int attemptCount;
    @Column(name="next_attempt_at",nullable=false) private Instant nextAttemptAt;
    @Column(name="last_failure",length=40) private String lastFailure;
    @Column(name="dead_letter",nullable=false) private boolean deadLetter;
    @Version @Column(nullable=false) private long version;
    public static PaymentWebhookEvent receive(Long attempt,String provider,String merchant,PaymentEnvironment env,String key,String digest) {
        return receive(attempt,provider,merchant,
                merchant==null?null:com.capdi.backend.domain.payment.gateway.ProviderAccountIdentity.legacyMerchant(provider,merchant),
                env,key,digest,Instant.now());
    }
    public static PaymentWebhookEvent receive(Long attempt,String provider,String merchant,PaymentEnvironment env,String key,String digest,Instant now) {
        return receive(attempt,provider,merchant,
                merchant==null?null:com.capdi.backend.domain.payment.gateway.ProviderAccountIdentity.legacyMerchant(provider,merchant),
                env,key,digest,now);
    }
    public static PaymentWebhookEvent receive(Long attempt,String provider,String merchant,String providerAccountId,
            PaymentEnvironment env,String key,String digest,Instant now) {
        if(providerAccountId==null || providerAccountId.isBlank())throw new IllegalArgumentException("Provider account identity required");
        var e=new PaymentWebhookEvent();e.attemptId=attempt;e.providerCode=provider;e.merchantId=merchant;e.providerAccountId=providerAccountId;
        e.environment=env;e.eventKeyHash=key;e.payloadHash=digest;e.state=State.RECEIVED;
        e.attemptCount=0;e.nextAttemptAt=now;e.deadLetter=false;return e;
    }
    public boolean claim(String token,Instant now,Duration lease,int maxAttempts) {
        if(state==State.PROCESSED || deadLetter || nextAttemptAt.isAfter(now)
                || state==State.PROCESSING && leaseUntil!=null && leaseUntil.isAfter(now)) return false;
        if(attemptCount>=maxAttempts) {
            deadLetter=true;lastFailure="PROCESS_INTERRUPTED";state=State.RECEIVED;
            processingToken=null;leaseUntil=null;return false;
        }
        attemptCount++;
        state=State.PROCESSING;processingToken=token;leaseUntil=now.plus(lease);return true;
    }
    public boolean finish(String token,Instant now,Outcome outcome,String failureCode,int maxAttempts,Duration retryDelay) {
        if(!Objects.equals(token,processingToken) || state!=State.PROCESSING || leaseUntil==null || !leaseUntil.isAfter(now))return false;
        processingToken=null;leaseUntil=null;
        if(outcome==Outcome.SUCCESS) {
            state=State.PROCESSED;lastFailure=null;nextAttemptAt=now;return true;
        }
        lastFailure=failureCode;
        state=State.RECEIVED;
        if(outcome==Outcome.PERMANENT_FAILURE || attemptCount>=maxAttempts) {
            deadLetter=true;nextAttemptAt=now;
        } else nextAttemptAt=now.plus(retryDelay);
        return true;
    }
    public enum Outcome { SUCCESS, TRANSIENT_FAILURE, PERMANENT_FAILURE }
}
