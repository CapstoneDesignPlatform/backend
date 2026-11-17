package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="payment_tax_invoice_drafts",uniqueConstraints=@UniqueConstraint(name="uk_tax_draft_request",
        columnNames={"settlement_id","request_key_hash"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PaymentTaxInvoiceDraft extends BaseTimeEntity {
    public enum Kind { EXPERT_TO_CLIENT, PLATFORM_TO_EXPERT }
    public enum State { TAX_REVIEW_HOLD }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="settlement_id",nullable=false,updatable=false) private Long settlementId;
    @Column(name="request_key_hash",nullable=false,length=64,updatable=false) private String requestKeyHash;
    @Column(name="original_draft_id",updatable=false) private Long originalDraftId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30,updatable=false) private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private State state;
    @Version @Column(nullable=false) private long version;
    public static PaymentTaxInvoiceDraft create(Long settlement,String key,Kind kind,Long original) {
        if(settlement==null || key==null || !key.matches("[a-f0-9]{64}") || kind==null)
            throw new IllegalArgumentException("Invalid tax draft");
        var t=new PaymentTaxInvoiceDraft();t.settlementId=settlement;t.requestKeyHash=key;t.kind=kind;t.originalDraftId=original;
        t.state=State.TAX_REVIEW_HOLD;return t;
    }
}
