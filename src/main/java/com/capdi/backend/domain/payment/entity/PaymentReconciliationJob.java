package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="payment_reconciliation_jobs",uniqueConstraints=@UniqueConstraint(name="uk_payment_job_attempt",columnNames="attempt_id"))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PaymentReconciliationJob extends BaseTimeEntity {
    public enum State { PENDING, RUNNING, COMPLETED, BLOCKED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="attempt_id",nullable=false,updatable=false) private Long attemptId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State state;
    @Column(name="due_at",nullable=false) private Instant dueAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private PaymentProcessingFailure failure;
    @Version @Column(nullable=false) private long version;
    public static PaymentReconciliationJob create(Long attempt,Instant due) {
        var j=new PaymentReconciliationJob();j.attemptId=attempt;j.pending(due,PaymentProcessingFailure.NONE);return j;
    }
    public void pending(Instant due,PaymentProcessingFailure reason) {state=State.PENDING;dueAt=due;failure=reason;}
    public void running() {state=State.RUNNING;}
    public void complete() {state=State.COMPLETED;failure=PaymentProcessingFailure.NONE;}
    public void block(PaymentProcessingFailure reason) {state=State.BLOCKED;failure=reason;}
    @Column(name="worker_token",length=36) private String workerToken;
    @Column(name="worker_lease_until") private Instant workerLeaseUntil;
    @Column(name="lookup_count",nullable=false) private int lookupCount;
    public String claimWorker(Instant now) {
        if(state==State.COMPLETED || state==State.BLOCKED || dueAt.isAfter(now)
                || workerLeaseUntil!=null && workerLeaseUntil.isAfter(now))return null;
        if(lookupCount>=8){block(PaymentProcessingFailure.PROCESS_INTERRUPTED);return null;}
        workerToken=java.util.UUID.randomUUID().toString();workerLeaseUntil=now.plusSeconds(45);lookupCount++;return workerToken;
    }
    public boolean finishWorker(String token,Instant now,PaymentProcessingFailure reason){
        if(token==null || !token.equals(workerToken))return false;
        workerToken=null;workerLeaseUntil=null;
        if(state!=State.COMPLETED && state!=State.BLOCKED){
            if(lookupCount>=8)block(reason==PaymentProcessingFailure.NONE?PaymentProcessingFailure.PROCESS_INTERRUPTED:reason);
            else pending(now.plusSeconds(Math.min(3600,30L*(1L<<Math.min(lookupCount-1,7)))),reason);
        }
        return true;
    }
}
