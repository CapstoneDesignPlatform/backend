package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.announcement.entity.AnnouncementProgressStepEnum;
import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.PaymentGateway;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.domain.payment.util.PaymentProcessingKeys;
import com.capdi.backend.global.exception.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.util.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly=true)
public class PaymentExecutionPersistenceService {
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final PaymentReconciliationJobRepository jobs;
    private final PaymentSourceRepository sources;
    private final PaymentGateway gateway;
    private final EntityManager em;
    private final Clock clock;
    public record Execution(Long user,Long payment,Long attempt,String token,PaymentGateway.Reference reference,
                            PaymentAttemptResponse response,boolean run) { }

    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public Execution prepare(Long user,Long paymentId,String rawKey,PaymentMethod method) {
        String hash=PaymentProcessingKeys.requestKey(rawKey);
        requireClient(user,true);
        Payment snapshot=ownedPayment(user,paymentId,false);
        var announcement=sources.lockAnnouncement(snapshot.getAnnouncementCode());
        var bid=sources.lockBid(snapshot.getBidId(),snapshot.getAnnouncementCode());
        var selectedIds=sources.lockSelectedBidIds(snapshot.getAnnouncementCode());
        Payment p=ownedPayment(user,paymentId,true);
        if(p.getStatus()==PaymentStatus.CANCELED
                || !matchesSelectedSource(p,announcement.orElse(null),bid.orElse(null),selectedIds))throw conflict();
        if(method==null || p.getMethod()!=method)throw conflict();
        var existing=attempts.findByPaymentIdAndRequestKeyHash(paymentId,hash);
        if(existing.isPresent())return execution(user,p,refresh(existing.get()),false);
        if(p.getStatus()!=PaymentStatus.CREATED || p.getActiveAttemptId()!=null)throw conflict();
        var active=attempts.findByPaymentIdAndStatusIn(paymentId,List.of(PaymentAttemptStatus.PREPARED,
                PaymentAttemptStatus.REQUESTED,PaymentAttemptStatus.WAITING_DEPOSIT,PaymentAttemptStatus.UNKNOWN,PaymentAttemptStatus.SUCCEEDED));
        if(!active.isEmpty())throw conflict();
        var identity=PaymentGatewaySafety.require(gateway,PaymentGateway.Capability.PREPARE);
        int number=attempts.findFirstByPaymentIdOrderByAttemptNumberDesc(paymentId).map(a->a.getAttemptNumber()+1).orElse(1);
        var a=attempts.saveAndFlush(PaymentAttempt.start(p,number,hash,identity,clock.instant()));
        p.beginAttempt(a.getId());
        jobs.saveAndFlush(PaymentReconciliationJob.create(a.getId(),a.getLeaseUntil()));
        payments.flush();
        return execution(user,p,a,true);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public Execution confirm(Long user,Long paymentId,Long attemptId,String reference) {
        if(reference==null || !reference.matches("[A-Za-z0-9._:-]{1,200}"))throw new CustomException(ErrorCode.INVALID_INPUT);
        Payment p=owned(user,paymentId,true);PaymentAttempt a=attempt(p,attemptId);
        String hash=PaymentProcessingKeys.digest(reference);
        if(a.getConfirmationHash()!=null) {
            if(!hash.equals(a.getConfirmationHash()))throw new CustomException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
            return execution(user,p,a,false);
        }
        if(!p.ownsAttempt(a.getId()) || p.getStatus()!=PaymentStatus.PROCESSING
                || a.getStatus()!=PaymentAttemptStatus.PREPARED || a.busy(clock.instant()))throw conflict();
        PaymentGatewaySafety.require(gateway,PaymentGateway.Capability.CONFIRM);
        if(!gateway.isIdentityCompatible(a.reference().identity()))throw conflict();
        a.claim(clock.instant());a.confirming(hash);job(a).pending(a.getLeaseUntil(),PaymentProcessingFailure.NONE);
        return execution(user,p,a,true);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public Execution lookup(Long user,Long paymentId,Long attemptId) {
        Payment p=owned(user,paymentId,true);PaymentAttempt a=attempt(p,attemptId);
        if(!p.ownsAttempt(a.getId()))throw conflict();
        if(p.getStatus()==PaymentStatus.FAILED || p.getStatus()==PaymentStatus.CANCELED)
            return execution(user,p,a,false);
        if(a.busy(clock.instant()))throw conflict();
        PaymentGatewaySafety.require(gateway,PaymentGateway.Capability.LOOKUP);
        if(!gateway.isIdentityCompatible(a.reference().identity()))throw conflict();
        a.claim(clock.instant());job(a).running();
        return execution(user,p,a,true);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public PaymentAttemptResponse complete(Execution execution,PaymentGateway.Observation result) {
        // Same lock order as order creation: payer -> announcement -> bids -> payment -> attempt.
        requireClient(execution.user(),true);
        Payment snapshot=ownedPayment(execution.user(),execution.payment(),false);
        var announcement=sources.lockAnnouncement(snapshot.getAnnouncementCode());
        var bid=sources.lockBid(snapshot.getBidId(),snapshot.getAnnouncementCode());
        var selectedIds=sources.lockSelectedBidIds(snapshot.getAnnouncementCode());
        Payment p=ownedPayment(execution.user(),execution.payment(),true);
        PaymentAttempt a=attempt(p,execution.attempt());
        verifyToken(p,a,execution.token());
        if(!PaymentGatewaySafety.matches(a,result))return uncertain(p,a,PaymentProcessingFailure.RESPONSE_MISMATCH);
        if(p.getStatus()==PaymentStatus.PAID && result.state()!=PaymentGateway.State.PAID)
            return uncertain(p,a,PaymentProcessingFailure.INCONSISTENT_STATE);
        if(result.state()==PaymentGateway.State.PAID
                && !matchesSelectedSource(p,announcement.orElse(null),bid.orElse(null),selectedIds))
            return uncertain(p,a,PaymentProcessingFailure.INCONSISTENT_STATE);
        a.observed(result);
        PaymentStatus next=switch(a.getStatus()) {
            case PREPARED,REQUESTED -> PaymentStatus.PROCESSING;
            case WAITING_DEPOSIT -> PaymentStatus.WAITING_DEPOSIT;
            case SUCCEEDED -> PaymentStatus.PAID;
            case FAILED -> PaymentStatus.FAILED;
            case CANCELED -> PaymentStatus.CANCELED;
            case UNKNOWN -> PaymentStatus.UNKNOWN;
        };
        p.recordAttempt(a.getId(),next,clock.instant());
        if(next==PaymentStatus.PAID
                && AnnouncementProgressStepEnum.STEP_3_EXPERT_SELECTION.name().equals(announcement.orElseThrow().progressStep())) {
            if(sources.startDiagnosisAfterPayment(p.getAnnouncementId())!=1)
                throw new IllegalStateException("Payment announcement transition failed");
        }
        var j=job(a);
        if(next==PaymentStatus.PAID || next==PaymentStatus.FAILED || next==PaymentStatus.CANCELED)
            j.complete();
        else j.pending(clock.instant().plusSeconds(30),PaymentProcessingFailure.NONE);
        attempts.flush();payments.flush();
        return PaymentAttemptResponse.from(a,p);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public PaymentAttemptResponse failed(Execution execution,PaymentProcessingFailure reason) {
        Payment p=owned(execution.user(),execution.payment(),true);PaymentAttempt a=attempt(p,execution.attempt());
        verifyToken(p,a,execution.token());return uncertain(p,a,reason);
    }
    public PaymentAttemptResponse read(Long user,Long paymentId,Long attemptId) {
        Payment p=owned(user,paymentId,false);
        var a=attempts.findById(attemptId).orElseThrow(()->new CustomException(ErrorCode.NOT_FOUND));
        if(!a.getPayment().getId().equals(paymentId))throw new CustomException(ErrorCode.NOT_FOUND);
        return PaymentAttemptResponse.from(a,p);
    }
    public Execution locate(Long attemptId) {
        PaymentAttempt a=attempts.findById(attemptId).orElseThrow(()->new CustomException(ErrorCode.NOT_FOUND));
        Payment p=a.getPayment();return execution(p.getClientUserId(),p,a,false);
    }
    private PaymentAttemptResponse uncertain(Payment p,PaymentAttempt a,PaymentProcessingFailure reason) {
        if(p.getStatus()==PaymentStatus.PAID) {
            a.hold(reason);job(a).block(reason);return PaymentAttemptResponse.from(a,p);
        }
        a.uncertain(reason);p.recordAttempt(a.getId(),PaymentStatus.UNKNOWN,clock.instant());
        job(a).pending(clock.instant().plusSeconds(30),reason);return PaymentAttemptResponse.from(a,p);
    }
    private void verifyToken(Payment p,PaymentAttempt a,String token) {
        if(!p.ownsAttempt(a.getId()) || !a.tokenMatches(token))throw new CustomException(ErrorCode.PAYMENT_EXECUTION_STALE);
    }
    private Payment owned(Long user,Long id,boolean lock) {
        requireClient(user,lock);
        return ownedPayment(user,id,lock);
    }
    private void requireClient(Long user,boolean lock) {
        if(user==null)throw new CustomException(ErrorCode.UNAUTHORIZED);
        String role=sources.userType(user,lock).orElseThrow(()->new CustomException(ErrorCode.UNAUTHORIZED));
        if(!"CLIENT".equals(role))throw new CustomException(ErrorCode.FORBIDDEN);
    }
    private Payment ownedPayment(Long user,Long id,boolean lock) {
        Payment p=(lock?payments.lockById(id):payments.findById(id)).orElseThrow(()->new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        if(lock)em.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        if(!p.getClientUserId().equals(user))throw new CustomException(ErrorCode.FORBIDDEN);
        return p;
    }
    private boolean matchesSelectedSource(Payment p,PaymentSourceRepository.AnnouncementSnapshot announcement,
                                          PaymentSourceRepository.BidSnapshot bid,List<Long> selectedIds) {
        if(announcement==null || bid==null || !p.getAnnouncementId().equals(announcement.id())
                || p.getSelectionRound()!=announcement.selectionRound()
                || !p.getClientUserId().equals(announcement.ownerId()) || !"CLOSED".equals(announcement.status())
                || !p.getBidId().equals(bid.id()) || !"SELECTED".equals(bid.status())
                || !selectedIds.equals(List.of(p.getBidId())) || bid.finalAmount()==null
                || p.getAmount().compareTo(bid.finalAmount())!=0
                || !sources.expertProfileId(bid.expertUserId()).filter(p.getExpertProfileId()::equals).isPresent())
            return false;
        try {
            // Existing STEP_4+ orders can still settle; duplicate results never rewind later steps.
            return AnnouncementProgressStepEnum.valueOf(announcement.progressStep()).getStepNumber()
                    >= AnnouncementProgressStepEnum.STEP_3_EXPERT_SELECTION.getStepNumber();
        } catch(IllegalArgumentException | NullPointerException ignored) {return false;}
    }
    private PaymentAttempt attempt(Payment p,Long id) {
        PaymentAttempt a=attempts.findById(id).orElseThrow(()->new CustomException(ErrorCode.NOT_FOUND));refresh(a);
        if(!a.getPayment().getId().equals(p.getId()))throw new CustomException(ErrorCode.NOT_FOUND);return a;
    }
    private PaymentAttempt refresh(PaymentAttempt a) {em.refresh(a,LockModeType.PESSIMISTIC_WRITE);return a;}
    private PaymentReconciliationJob job(PaymentAttempt a) {
        var j=jobs.findByAttemptId(a.getId()).orElseGet(()->jobs.save(PaymentReconciliationJob.create(a.getId(),clock.instant())));
        if(j.getId()!=null)em.refresh(j);return j;
    }
    private Execution execution(Long user,Payment p,PaymentAttempt a,boolean run) {
        return new Execution(user,p.getId(),a.getId(),a.getExecutionToken(),a.reference(),PaymentAttemptResponse.from(a,p),run);
    }
    private CustomException conflict() {return new CustomException(ErrorCode.PAYMENT_EXECUTION_CONFLICT);}
}
