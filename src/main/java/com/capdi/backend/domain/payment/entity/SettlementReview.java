package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
@Entity @Table(name="settlement_reviews")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SettlementReview extends BaseTimeEntity {
 @Column(name="profile_review_id",updatable=false) private Long profileReviewId;
 public void captureProfileReview(Long id){profileReviewId=id;}
 public enum State { PENDING_APPROVAL, APPROVED, ON_HOLD, REJECTED }
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="payment_id",nullable=false,updatable=false) private Long paymentId;
 @Column(name="request_key",nullable=false,length=64,updatable=false) private String requestKey;
 @Column(name="balance_version",nullable=false,updatable=false) private long balanceVersion;
 @Column(name="profile_revision",nullable=false,updatable=false) private long profileRevision;
 @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal amount;
 @Column(name="requester_id",nullable=false,updatable=false) private Long requesterId;
 @Column(name="reviewer_id") private Long reviewerId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private State state;
 @Column(name="reason_code",length=100) private String reasonCode;
 @Version @Column(nullable=false) private long version;
 public static SettlementReview request(Long payment,String key,long balance,long profile,BigDecimal amount,Long actor){
  if(amount==null || amount.signum()<=0 || amount.stripTrailingZeros().scale()>0)throw new IllegalArgumentException("Whole KRW required");
  var r=new SettlementReview();r.paymentId=payment;r.requestKey=key;r.balanceVersion=balance;r.profileRevision=profile;r.amount=amount;r.requesterId=actor;r.state=State.PENDING_APPROVAL;return r;
 }
 public void decide(Long actor,State decision,String reason){
  if(state!=State.PENDING_APPROVAL || decision==null || decision==State.PENDING_APPROVAL || reason==null || !reason.matches("[A-Z0-9_]{1,100}"))throw new IllegalStateException("Review transition rejected");
  reviewerId=actor;state=decision;reasonCode=reason;
 }
 public boolean current(long balance,long profile){return balanceVersion==balance && profileRevision==profile;}
}
