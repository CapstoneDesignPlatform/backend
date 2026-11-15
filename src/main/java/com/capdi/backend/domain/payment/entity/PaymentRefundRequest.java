package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="payment_refund_requests",uniqueConstraints=@UniqueConstraint(name="uk_refund_payment_key",
        columnNames={"payment_id","request_key_hash"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PaymentRefundRequest extends BaseTimeEntity {
    public enum State { POLICY_HOLD, APPROVED, REQUESTED, UNKNOWN, SUCCEEDED, FAILED, REJECTED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="payment_id",nullable=false,updatable=false) private Long paymentId;
    @Column(name="request_key_hash",nullable=false,length=64,updatable=false) private String requestKeyHash;
    @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private State state;
    @Version @Column(nullable=false) private long version;
    public static PaymentRefundRequest draft(Long payment,String key,BigDecimal amount) {
        if(payment==null || key==null || !key.matches("[a-f0-9]{64}") || amount==null || amount.signum()<=0
                || amount.stripTrailingZeros().scale()>0)throw new IllegalArgumentException("Invalid refund draft");
        var r=new PaymentRefundRequest();r.paymentId=payment;r.requestKeyHash=key;r.amount=amount;r.state=State.POLICY_HOLD;return r;
    }
    public void approve(){if(state!=State.POLICY_HOLD && state!=State.APPROVED)throw new IllegalStateException("Refund not reviewable");state=State.APPROVED;}
    public void requested(){if(state!=State.APPROVED)throw new IllegalStateException("Refund not approved");state=State.REQUESTED;}
    public void observed(com.capdi.backend.domain.payment.gateway.FinancialGateway.State result) {
        if(state==State.SUCCEEDED)return;
        state=switch(result){case PENDING->State.REQUESTED;case UNKNOWN->State.UNKNOWN;case SUCCEEDED->State.SUCCEEDED;case FAILED->State.FAILED;};
    }
    public void rejectReview(){if(state!=State.POLICY_HOLD && state!=State.APPROVED)throw new IllegalStateException("Refund already dispatched");state=State.REJECTED;}
    public void holdReview(){if(state!=State.POLICY_HOLD && state!=State.APPROVED)throw new IllegalStateException("Refund already dispatched");state=State.POLICY_HOLD;}
}
