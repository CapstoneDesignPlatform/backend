package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.*;
@Entity @Table(name="settlement_balances")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SettlementBalance extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="payment_id",nullable=false,unique=true,updatable=false) private Long paymentId;
    @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal gross;
    @Column(name="platform_rate",nullable=false,precision=5,scale=4,updatable=false) private BigDecimal platformRate;
    @Column(name="policy_version",nullable=false,length=50,updatable=false) private String policyVersion;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal refunded;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal reserved;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal paid;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal recoverable;
    @Column(nullable=false) private boolean held;
    @Column(name="review_sequence",nullable=false) private long reviewSequence;
    @Version @Column(nullable=false) private long version;
    public static SettlementBalance from(Payment p) {
        if(p.getStatus()!=PaymentStatus.PAID)throw new IllegalArgumentException("Not paid");
        var s=new SettlementBalance();s.paymentId=p.getId();s.gross=p.getAmount();s.platformRate=p.getPlatformFeeRate();
        s.policyVersion=p.getFeePolicyVersion();s.refunded=BigDecimal.ZERO.setScale(2);s.reserved=s.refunded;s.paid=s.refunded;s.recoverable=s.refunded;
        s.held=!s.integralAllocation();return s;
    }
    public BigDecimal net(){return gross.subtract(refunded);}
    public BigDecimal platform(){return net().multiply(platformRate).setScale(2,RoundingMode.UNNECESSARY);}
    public BigDecimal expert(){return net().subtract(platform());}
    public boolean integralAllocation(){return platform().stripTrailingZeros().scale()<=0 && expert().stripTrailingZeros().scale()<=0;}
    public BigDecimal available(){return expert().subtract(reserved).subtract(paid).max(BigDecimal.ZERO);}
    public void refund(BigDecimal amount) {
        positive(amount);if(refunded.add(amount).compareTo(gross)>0)throw new IllegalStateException("Refund limit");
        refunded=refunded.add(amount);recoverable=paid.subtract(expert()).max(BigDecimal.ZERO);
        held=reserved.signum()>0 || recoverable.signum()>0 || !integralAllocation();
    }
    public void reserve(BigDecimal amount) {
        positive(amount);if(held || !integralAllocation() || available().compareTo(amount)<0)throw new IllegalStateException("Payout unavailable");
        reserved=reserved.add(amount);
    }
    public void paid(BigDecimal amount){positive(amount);if(reserved.compareTo(amount)<0)throw new IllegalStateException("Reservation mismatch");reserved=reserved.subtract(amount);paid=paid.add(amount);recoverable=paid.subtract(expert()).max(BigDecimal.ZERO);held=recoverable.signum()>0 || !integralAllocation() || refunded.signum()>0 && reserved.signum()>0;}
    public void release(BigDecimal amount){positive(amount);if(reserved.compareTo(amount)<0)throw new IllegalStateException("Reservation mismatch");reserved=reserved.subtract(amount);held=recoverable.signum()>0 || !integralAllocation() || refunded.signum()>0 && reserved.signum()>0;}
    public void hold(){held=true;}
    private void positive(BigDecimal x){if(x==null || x.signum()<=0 || x.stripTrailingZeros().scale()>0)throw new IllegalArgumentException("Whole KRW required");}
    public void review(){reviewSequence++;}
}
