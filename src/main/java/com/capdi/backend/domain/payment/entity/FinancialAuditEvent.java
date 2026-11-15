package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="financial_audit_events")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class FinancialAuditEvent extends BaseTimeEntity {
 @Column(name="document_id",updatable=false) private Long documentId;
 @Column(name="reason_code",length=100,updatable=false) private String reasonCode;
 @Column(name="snapshot_hash",length=64,updatable=false) private String snapshotHash;
 public FinancialAuditEvent document(Long id){documentId=id;return this;}
 public FinancialAuditEvent evidence(String reason,String hash){reasonCode=reason;snapshotHash=hash;return this;}
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="payout_id",updatable=false) private Long payoutId;
 @Column(name="tax_execution_id",updatable=false) private Long taxExecutionId;
 @Column(name="profile_id",updatable=false) private Long profileId;
 @Column(name="actor_id",updatable=false) private Long actorId;
 @Column(name="event_key",nullable=false,unique=true,length=150,updatable=false) private String eventKey;
 @Column(nullable=false,length=50,updatable=false) private String code;
 @Column(name="state_code",nullable=false,length=40,updatable=false) private String stateCode;
 public static FinancialAuditEvent record(Long payout,Long tax,Long profile,Long actor,String key,String code,String state){
  var e=new FinancialAuditEvent();e.payoutId=payout;e.taxExecutionId=tax;e.profileId=profile;e.actorId=actor;e.eventKey=key;e.code=code;e.stateCode=state;return e;
 }
}
