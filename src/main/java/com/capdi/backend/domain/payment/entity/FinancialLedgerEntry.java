package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="financial_ledger_entries",uniqueConstraints=@UniqueConstraint(name="uk_ledger_movement_bucket",columnNames={"movement_key","bucket"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class FinancialLedgerEntry extends BaseTimeEntity {
    public enum Bucket { SETTLEMENT_CLEARING, PLATFORM_FEE, EXPERT_PAYABLE, PAYOUT_RESERVED, PAYOUT_PAID, RECOVERY_RECEIVABLE }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="payment_id",nullable=false,updatable=false) private Long paymentId;
    @Column(name="operation_id",updatable=false) private Long operationId;
    @Column(name="movement_key",nullable=false,length=100,updatable=false) private String movementKey;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30,updatable=false) private Bucket bucket;
    @Column(name="signed_amount",nullable=false,precision=15,scale=2,updatable=false) private BigDecimal signedAmount;
    public static FinancialLedgerEntry line(Long payment,Long operation,String movement,Bucket bucket,BigDecimal amount) {
        var l=new FinancialLedgerEntry();l.paymentId=payment;l.operationId=operation;l.movementKey=movement;l.bucket=bucket;l.signedAmount=amount;return l;
    }
}
