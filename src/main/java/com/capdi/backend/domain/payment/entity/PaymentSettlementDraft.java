package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="payment_settlement_drafts",uniqueConstraints=@UniqueConstraint(name="uk_settlement_draft_payment",columnNames="payment_id"))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PaymentSettlementDraft extends BaseTimeEntity {
    public enum State { POLICY_HOLD }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="payment_id",nullable=false,updatable=false) private Long paymentId;
    @Column(name="expert_profile_id",nullable=false,updatable=false) private Long expertProfileId;
    @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal gross;
    @Column(name="platform_amount",nullable=false,precision=15,scale=2,updatable=false) private BigDecimal platformAmount;
    @Column(name="expert_amount",nullable=false,precision=15,scale=2,updatable=false) private BigDecimal expertAmount;
    @Column(name="policy_version",nullable=false,length=50,updatable=false) private String policyVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private State state;
    @Version @Column(nullable=false) private long version;
    public static PaymentSettlementDraft create(Payment p) {
        if(p.getId()==null || p.getStatus()!=PaymentStatus.PAID)throw new IllegalArgumentException("Payment not paid");
        var d=new PaymentSettlementDraft();d.paymentId=p.getId();d.expertProfileId=p.getExpertProfileId();
        d.gross=p.getAmount();d.platformAmount=p.getAmount().multiply(p.getPlatformFeeRate()).setScale(2,java.math.RoundingMode.UNNECESSARY);
        d.expertAmount=d.gross.subtract(d.platformAmount);d.policyVersion=p.getFeePolicyVersion();d.state=State.POLICY_HOLD;return d;
    }
}
