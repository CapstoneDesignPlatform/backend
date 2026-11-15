package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="settlement_profile_reviews")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SettlementProfileReview extends BaseTimeEntity {
 public enum Decision { APPROVED, ON_HOLD, REJECTED }
 public enum Documents { ACCEPTED, MISSING, REJECTED }
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="profile_id",nullable=false,updatable=false) private Long profileId;
 @Column(name="admin_id",nullable=false,updatable=false) private Long adminId;
 @Column(name="profile_revision",nullable=false,updatable=false) private long profileRevision;
 @Column(name="request_key",nullable=false,length=64,updatable=false) private String requestKey;
 @Column(name="request_hash",nullable=false,length=64,updatable=false) private String requestHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private Decision decision;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,updatable=false) private Documents documents;
 @Column(name="reason_code",nullable=false,length=100,updatable=false) private String reasonCode;
 public static SettlementProfileReview record(Long profile,Long admin,long revision,String key,String hash,Decision decision,Documents documents,String reason){
  if(decision==Decision.APPROVED && documents!=Documents.ACCEPTED)throw new IllegalArgumentException("Documents required");
  var r=new SettlementProfileReview();r.profileId=profile;r.adminId=admin;r.profileRevision=revision;r.requestKey=key;r.requestHash=hash;r.decision=decision;r.documents=documents;r.reasonCode=reason;return r;
 }
}
