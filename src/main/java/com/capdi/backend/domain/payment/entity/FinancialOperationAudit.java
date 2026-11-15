package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="financial_operation_audits")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class FinancialOperationAudit extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="operation_id",nullable=false,updatable=false) private Long operationId;
 @Column(name="actor_id",updatable=false) private Long actorId;
 @Column(nullable=false,length=40,updatable=false) private String action;
 @Column(name="state_code",nullable=false,length=60,updatable=false) private String stateCode;
 public static FinancialOperationAudit record(Long operation,Long actor,String action,String state) {
  var a=new FinancialOperationAudit();a.operationId=operation;a.actorId=actor;a.action=action;a.stateCode=state;return a;
 }
}
