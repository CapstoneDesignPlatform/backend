package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.repository.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PaymentLookupLeaseService {
 private final PaymentReconciliationJobRepository jobs;private final PaymentLookupAuditRepository audits;
 private final EntityManager em;private final Clock clock;
 public record Lease(Long job,Long attempt,String token,boolean run){}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public Lease claim(Long jobId){
  var j=jobs.findById(jobId).orElseThrow();em.refresh(j,LockModeType.PESSIMISTIC_WRITE);
  String token=j.claimWorker(clock.instant());return new Lease(j.getId(),j.getAttemptId(),token,token!=null);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void finish(Lease lease,PaymentProcessingFailure failure){
  var j=jobs.findById(lease.job()).orElseThrow();em.refresh(j,LockModeType.PESSIMISTIC_WRITE);
  if(!j.finishWorker(lease.token(),clock.instant(),failure))return;
  audits.save(PaymentLookupAudit.record(j.getId(),j.getAttemptId(),failure.name()));
 }
}
