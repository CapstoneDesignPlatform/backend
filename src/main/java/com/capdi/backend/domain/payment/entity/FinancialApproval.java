package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="financial_approvals")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class FinancialApproval extends BaseTimeEntity {
    public enum Kind { REFUND, PAYOUT }
    public enum Decision { APPROVED, HOLD, REJECTED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="admin_user_id",nullable=false,updatable=false) private Long adminUserId;
    @Column(name="payment_id",nullable=false,updatable=false) private Long paymentId;
    @Column(name="refund_request_id",updatable=false) private Long refundRequestId;
    @Column(name="account_id",updatable=false) private Long accountId;
    @Column(name="balance_version",nullable=false,updatable=false) private long balanceVersion;
    @Column(name="account_revision",updatable=false) private Long accountRevision;
    @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private Decision decision;
    @Column(name="reason_code",nullable=false,length=100,updatable=false) private String reasonCode;
    @Version @Column(nullable=false) private long version;
    public static FinancialApproval create(Long admin,Long payment,Long refund,ExpertSettlementAccount account,long balanceVersion,
            BigDecimal amount,Kind kind,Decision decision,String reason) {
        var a=new FinancialApproval();a.adminUserId=admin;a.paymentId=payment;a.refundRequestId=refund;
        a.accountId=account==null?null:account.getId();a.accountRevision=account==null?null:account.getAccountRevision();
        a.balanceVersion=balanceVersion;a.amount=amount;a.kind=kind;a.decision=decision;a.reasonCode=reason;return a;
    }
}
