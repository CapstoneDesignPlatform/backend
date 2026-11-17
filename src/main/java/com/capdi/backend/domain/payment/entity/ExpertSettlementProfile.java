package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="expert_settlement_profiles")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ExpertSettlementProfile extends BaseTimeEntity {
 public enum BusinessType { INDIVIDUAL, SOLE_PROPRIETOR, CORPORATION }
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="expert_profile_id",nullable=false,updatable=false) private Long expertProfileId;
 @Column(nullable=false,updatable=false) private long revision;
 @Enumerated(EnumType.STRING) @Column(name="business_type",nullable=false,length=30,updatable=false) private BusinessType businessType;
 @Column(name="token_reference",nullable=false,length=100,updatable=false) private String tokenReference;
 @Column(name="business_info_id",nullable=false,updatable=false) private Long businessInfoId;
 @Column(name="input_revision",nullable=false,updatable=false) private long inputRevision;
 @Column(name="ocr_revision",nullable=false,updatable=false) private long ocrRevision;
 @Column(name="file_id",nullable=false,updatable=false) private Long fileId;
 public static ExpertSettlementProfile create(Long expert,long revision,BusinessType type,String token,Long info,long input,long ocr,Long file){
  // Local virtual tokens cannot be interpreted as verified bank accounts.
  if(token==null || !token.matches("virtual_[A-Za-z0-9_-]{8,80}") || revision<1 || type==null)throw new IllegalArgumentException("Virtual token required");
  var p=new ExpertSettlementProfile();p.expertProfileId=expert;p.revision=revision;p.businessType=type;p.tokenReference=token;p.businessInfoId=info;p.inputRevision=input;p.ocrRevision=ocr;p.fileId=file;return p;
 }
}
