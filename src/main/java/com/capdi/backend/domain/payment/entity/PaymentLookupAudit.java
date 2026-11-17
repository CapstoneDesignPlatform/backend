package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="payment_lookup_audits")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PaymentLookupAudit extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="job_id",nullable=false,updatable=false) private Long jobId;
 @Column(name="attempt_id",nullable=false,updatable=false) private Long attemptId;
 @Column(name="result_code",nullable=false,length=40,updatable=false) private String resultCode;
 public static PaymentLookupAudit record(Long job,Long attempt,String result){var a=new PaymentLookupAudit();a.jobId=job;a.attemptId=attempt;a.resultCode=result;return a;}
}
