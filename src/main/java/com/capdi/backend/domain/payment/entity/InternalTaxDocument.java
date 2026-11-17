package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Table(name="internal_tax_documents") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class InternalTaxDocument extends BaseTimeEntity {
 @Column(name="balance_version") private Long balanceVersion;
 public void captureBalance(long version){if(balanceVersion!=null)throw new IllegalStateException("Snapshot immutable");balanceVersion=version;}
 @Column(name="profile_review_id") private Long profileReviewId;
 @Column(name="reviewed_policy_version",length=100) private String reviewedPolicyVersion;
 public void captureProfileReview(Long id){profileReviewId=id;}
 public void recordPolicy(String policy){reviewedPolicyVersion=state==State.APPROVED?policy:null;}
 @Column(name="profile_id") private Long profileId;
 public void captureProfile(Long profile){profileId=profile;}
 public void refreshSource(long balance,Long profile){balanceVersion=balance;profileId=profile;state=State.POLICY_HOLD;reviewerId=null;reasonCode=null;reviewedPolicyVersion=null;}
 @Column(name="revision_of_id",updatable=false) private Long revisionOfId;
 @Column(nullable=false) private boolean superseded;
 public void revisionOf(Long id){revisionOfId=id;}
 public void supersede(){superseded=true;}
 public enum State { POLICY_HOLD, APPROVED, REJECTED }
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="payment_id",nullable=false,updatable=false) private Long paymentId;
 @Column(name="scope_key",nullable=false,unique=true,length=64,updatable=false) private String scopeKey;
 @Column(name="request_hash",nullable=false,length=64,updatable=false) private String requestHash;
 @Column(name="original_document_id",updatable=false) private Long originalDocumentId;
 @Column(name="supplier_snapshot_id",nullable=false,updatable=false) private Long supplierSnapshotId;
 @Column(name="recipient_snapshot_id",nullable=false,updatable=false) private Long recipientSnapshotId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30,updatable=false) private PaymentTaxInvoiceDraft.Kind kind;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private FinancialTaxInvoice.Taxation taxation;
 @Enumerated(EnumType.STRING) @Column(length=50,updatable=false) private FinancialTaxInvoice.Modification modification;
 @Column(name="write_date",nullable=false,updatable=false) private LocalDate writeDate;
 @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal supply;
 @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal vat;
 @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal total;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State state;
 @Column(name="reviewer_id") private Long reviewerId;
 @Column(name="reason_code",length=100) private String reasonCode;
 @Version @Column(nullable=false) private long version;
 public static InternalTaxDocument draft(Long payment,String scope,String hash,Long original,Long supplier,Long recipient,
 PaymentTaxInvoiceDraft.Kind kind,FinancialTaxInvoice.Taxation taxation,FinancialTaxInvoice.Modification modification,LocalDate date,BigDecimal supply,BigDecimal vat,BigDecimal total){
  if(date==null || kind==null || taxation==null || supply==null || vat==null || total==null || supply.add(vat).compareTo(total)!=0
   || total.signum()==0 || supplier==null || recipient==null || supplier.equals(recipient) || (original==null)!=(modification==null)
   || original==null && (supply.signum()<0 || vat.signum()<0) || taxation!=FinancialTaxInvoice.Taxation.TAXABLE && vat.signum()!=0)throw new IllegalArgumentException("Invalid invoice totals");
  for(var x:java.util.List.of(supply,vat,total))if(x.stripTrailingZeros().scale()>0 || x.abs().compareTo(new BigDecimal("9999999999999"))>0)throw new IllegalArgumentException("Whole KRW required");
  var d=new InternalTaxDocument();d.paymentId=payment;d.scopeKey=scope;d.requestHash=hash;d.originalDocumentId=original;d.supplierSnapshotId=supplier;d.recipientSnapshotId=recipient;d.kind=kind;d.taxation=taxation;d.modification=modification;d.writeDate=date;d.supply=supply;d.vat=vat;d.total=total;d.state=State.POLICY_HOLD;return d;
 }
 public void review(Long actor,boolean approve,String reason,boolean policyReviewed){
  if(state!=State.POLICY_HOLD)throw new IllegalStateException("Review immutable");
  reviewerId=actor;reasonCode=reason;state=approve?(policyReviewed?State.APPROVED:State.POLICY_HOLD):State.REJECTED;
 }
}
