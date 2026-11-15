package com.capdi.backend.domain.payment.entity;

import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_attempt_key", columnNames = "attempt_key"),
        @UniqueConstraint(name = "uk_payment_attempt_number", columnNames = {"payment_id", "attempt_number"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;
    @Column(name = "attempt_key", nullable = false, length = 36, updatable = false)
    private String attemptKey;
    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PaymentAttemptStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PaymentProvider provider;
    @Version @Column(nullable = false)
    private long version;

    public static PaymentAttempt prepare(Payment payment, int number) {
        if (payment == null || payment.getId() == null || number < 1) {
            throw new IllegalArgumentException("Invalid payment attempt");
        }
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.payment = payment;
        attempt.attemptKey = UUID.randomUUID().toString();
        attempt.attemptNumber = number;
        attempt.status = PaymentAttemptStatus.PREPARED;
        attempt.provider = PaymentProvider.DISABLED;
        attempt.method=payment.getMethod();attempt.chargeAmount=payment.getAmount();
        attempt.currency=payment.getCurrency();attempt.orderId=payment.getOrderId();
        return attempt;
    }

    @Column(name="method",nullable=false,length=30) @Enumerated(EnumType.STRING) private PaymentMethod method;
    @Column(name="charge_amount",nullable=false,precision=15,scale=2) private java.math.BigDecimal chargeAmount;
    @Column(name="currency",nullable=false,length=3) private String currency;
    @Column(name="order_id",nullable=false,length=40) private String orderId;
    @Column(name="request_key_hash",length=64,updatable=false) private String requestKeyHash;
    @Column(name="confirmation_hash",length=64) private String confirmationHash;
    @Column(name="provider_code",nullable=false,length=40) private String providerCode="DISABLED";
    @Column(name="merchant_id",length=100) private String merchantId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PaymentEnvironment environment=PaymentEnvironment.DISABLED;
    @Column(name="provider_transaction_id",length=200) private String providerTransactionId;
    @Column(name="provider_raw_status",length=80) private String providerRawStatus;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private PaymentProcessingFailure failure=PaymentProcessingFailure.NONE;
    @Column(name="execution_token",length=36) private String executionToken;
    @Column(name="lease_until") private java.time.Instant leaseUntil;

    public static PaymentAttempt start(Payment p,int number,String key,
            com.capdi.backend.domain.payment.gateway.PaymentGateway.Identity identity,java.time.Instant now) {
        var a=prepare(p,number);a.requestKeyHash=key;a.provider=PaymentProvider.ADAPTER;
        a.providerCode=identity.code();a.merchantId=identity.merchant();a.environment=identity.environment();a.claim(now);return a;
    }
    public String claim(java.time.Instant now) {
        if(busy(now))throw new IllegalStateException("Payment execution already leased");
        executionToken=UUID.randomUUID().toString();leaseUntil=now.plusSeconds(30);return executionToken;
    }
    public boolean busy(java.time.Instant now) {return executionToken!=null && leaseUntil!=null && leaseUntil.isAfter(now);}
    public boolean tokenMatches(String token) {return token!=null && token.equals(executionToken);}
    public void confirming(String hash) {confirmationHash=hash;status=PaymentAttemptStatus.REQUESTED;}
    public void observed(com.capdi.backend.domain.payment.gateway.PaymentGateway.Observation result) {
        if(result.transactionId()!=null)providerTransactionId=result.transactionId();
        providerRawStatus=result.rawStatus();failure=PaymentProcessingFailure.NONE;
        status=switch(result.state()) {
            case PREPARED -> PaymentAttemptStatus.PREPARED;
            case REQUESTED -> PaymentAttemptStatus.REQUESTED;
            case WAITING_DEPOSIT -> PaymentAttemptStatus.WAITING_DEPOSIT;
            case PAID -> PaymentAttemptStatus.SUCCEEDED;
            case FAILED -> PaymentAttemptStatus.FAILED;
            case CANCELED -> PaymentAttemptStatus.CANCELED;
            case UNKNOWN -> PaymentAttemptStatus.UNKNOWN;
        };
        executionToken=null;leaseUntil=null;
    }
    public void hold(PaymentProcessingFailure reason) {failure=reason;executionToken=null;leaseUntil=null;}
    public void uncertain(PaymentProcessingFailure reason) {
        status=PaymentAttemptStatus.UNKNOWN;failure=reason;executionToken=null;leaseUntil=null;
    }
    public com.capdi.backend.domain.payment.gateway.PaymentGateway.Reference reference() {
        return new com.capdi.backend.domain.payment.gateway.PaymentGateway.Reference(
                new com.capdi.backend.domain.payment.gateway.PaymentGateway.Identity(providerCode,merchantId,environment),
                orderId,attemptKey,providerTransactionId,chargeAmount,currency,method);
    }
}
