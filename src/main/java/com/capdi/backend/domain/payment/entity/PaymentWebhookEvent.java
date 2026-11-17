package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="payment_webhook_events",uniqueConstraints=@UniqueConstraint(name="uk_payment_event_identity",
        columnNames={"provider_code","merchant_id","environment","event_key_hash"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PaymentWebhookEvent extends BaseTimeEntity {
    public enum State { RECEIVED, PROCESSING, PROCESSED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="attempt_id",nullable=false,updatable=false) private Long attemptId;
    @Column(name="provider_code",nullable=false,length=40,updatable=false) private String providerCode;
    @Column(name="merchant_id",nullable=false,length=100,updatable=false) private String merchantId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private PaymentEnvironment environment;
    @Column(name="event_key_hash",nullable=false,length=64,updatable=false) private String eventKeyHash;
    @Column(name="payload_hash",nullable=false,length=64,updatable=false) private String payloadHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State state;
    @Column(name="processing_token",length=36) private String processingToken;
    @Column(name="lease_until") private Instant leaseUntil;
    @Version @Column(nullable=false) private long version;
    public static PaymentWebhookEvent receive(Long attempt,String provider,String merchant,PaymentEnvironment env,String key,String digest) {
        var e=new PaymentWebhookEvent();e.attemptId=attempt;e.providerCode=provider;e.merchantId=merchant;
        e.environment=env;e.eventKeyHash=key;e.payloadHash=digest;e.state=State.RECEIVED;return e;
    }
    public boolean claim(String token,Instant now) {
        if(state==State.PROCESSED || leaseUntil!=null && leaseUntil.isAfter(now)) return false;
        state=State.PROCESSING;processingToken=token;leaseUntil=now.plusSeconds(30);return true;
    }
    public void finish(String token,boolean processed) {
        if(!java.util.Objects.equals(token,processingToken))return;
        state=processed?State.PROCESSED:State.RECEIVED;processingToken=null;leaseUntil=null;
    }
}
