package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="internal_payouts") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class InternalPayout extends BaseTimeEntity {
 @Column(name="lookup_count",nullable=false) private int lookupCount;
 @Column(name="next_lookup_at") private Instant nextLookupAt;
 public boolean due(Instant now){return dispatched && lookupCount<8 && (state==State.REQUESTED || state==State.UNKNOWN) && (leaseUntil==null || !leaseUntil.isAfter(now)) && (nextLookupAt==null || !nextLookupAt.isAfter(now));}
 public void allowManualLookup(Instant now){if(!dispatched || leaseUntil!=null && leaseUntil.isAfter(now))throw new IllegalStateException("Busy");lookupCount=0;nextLookupAt=now;}
 public enum State { RESERVED, REQUESTED, UNKNOWN, SUCCEEDED, FAILED, CANCELED }
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=50,updatable=false) private String identity;
 @Column(name="payment_id",nullable=false,updatable=false) private Long paymentId;
 @Column(name="review_id",nullable=false,unique=true,updatable=false) private Long reviewId;
 @Column(name="profile_id",nullable=false,updatable=false) private Long profileId;
 @Column(name="balance_version",nullable=false,updatable=false) private long balanceVersion;
 @Column(nullable=false,precision=15,scale=2,updatable=false) private BigDecimal amount;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State state;
 @Column(name="external_id",length=200,unique=true) private String externalId;
 @Column(name="provider_code",nullable=false,length=50,updatable=false) private String providerCode;
 @Column(name="provider_environment",nullable=false,length=30,updatable=false) private String providerEnvironment;
 @Column(nullable=false) private boolean dispatched;
 @Column(length=50) private String lease;
 @Column(name="lease_until") private Instant leaseUntil;
 @Version @Column(nullable=false) private long version;
 public static InternalPayout reserve(Long payment,Long review,Long profile,long balance,BigDecimal amount,String provider,String environment){
  var p=new InternalPayout();p.paymentId=payment;p.reviewId=review;p.profileId=profile;p.balanceVersion=balance;p.amount=amount;p.providerCode=provider;p.providerEnvironment=environment;p.identity="payout_"+UUID.randomUUID();p.state=State.RESERVED;return p;
 }
 public String claim(boolean lookup,Instant now){
  if(leaseUntil!=null && leaseUntil.isAfter(now) || state==State.SUCCEEDED || state==State.FAILED || state==State.CANCELED)throw new IllegalStateException("Payout busy or terminal");
  if(lookup && (!dispatched || lookupCount>=8) || !lookup && (dispatched || state!=State.RESERVED))throw new IllegalStateException("No automatic resubmission");
  if(lookup)lookupCount++;else dispatched=true;
  state=State.REQUESTED;lease=UUID.randomUUID().toString();leaseUntil=now.plusSeconds(45);return lease;
 }
 public boolean owns(String token){return token!=null && token.equals(lease);}
 public void observe(String token,State next,String external,Instant now){
  if(!owns(token))throw new IllegalStateException("Stale result");
  if(externalId!=null && external!=null && !externalId.equals(external))throw new IllegalStateException("Payout identity mismatch");
  if(external!=null)externalId=external;
  state=next;lease=null;leaseUntil=null;nextLookupAt=now.plusSeconds(Math.min(3600,30L<<Math.min(lookupCount,6)));
 }
 public void cancel(){if(dispatched)throw new IllegalStateException("Payout already dispatched");state=State.CANCELED;}
}
