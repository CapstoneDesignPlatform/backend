package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="settlement_adjustments")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SettlementAdjustment extends BaseTimeEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="payment_id",nullable=false,updatable=false) private Long paymentId;
    @Column(name="operation_id",nullable=false,unique=true,updatable=false) private Long operationId;
    @Column(name="refund_amount",nullable=false,precision=15,scale=2,updatable=false) private BigDecimal refundAmount;
    @Column(name="net_before",nullable=false,precision=15,scale=2,updatable=false) private BigDecimal netBefore;
    @Column(name="net_after",nullable=false,precision=15,scale=2,updatable=false) private BigDecimal netAfter;
    public static SettlementAdjustment record(Long payment,Long operation,BigDecimal refund,BigDecimal before,BigDecimal after) {
        var a=new SettlementAdjustment();a.paymentId=payment;a.operationId=operation;a.refundAmount=refund;a.netBefore=before;a.netAfter=after;return a;
    }
}
