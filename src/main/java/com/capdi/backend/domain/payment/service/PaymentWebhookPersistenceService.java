package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.PaymentGateway;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.global.exception.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PaymentWebhookPersistenceService {
    private final PaymentWebhookEventRepository events;
    private final PaymentGateway gateway;
    private final PaymentAttemptRepository attempts;
    private final PaymentRepository payments;
    private final PaymentSourceRepository sources;
    private final PaymentReconciliationJobRepository jobs;
    private final EntityManager em;
    private final Clock clock;
    private final PaymentWebhookRetryPolicy retryPolicy;
    public record Receipt(Long id,boolean duplicate) { }
    public record Claim(Long id,Long attempt,String token,boolean run,int attemptCount) { }

    @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.READ_COMMITTED)
    public Receipt record(PaymentGateway.VerifiedEvent verified,String eventHash,String payloadHash) {
        var a=attempts.findByAttemptKey(verified.attemptKey()).orElseThrow(()->new CustomException(ErrorCode.PAYMENT_EVENT_INVALID));
        Long owner=a.getPayment().getClientUserId();
        if(!"CLIENT".equals(sources.userType(owner,true).orElse("")))throw invalid();
        var p=payments.lockById(a.getPayment().getId()).orElseThrow(this::invalid);em.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        em.refresh(a,LockModeType.PESSIMISTIC_WRITE);
        if(!p.ownsAttempt(a.getId()) || !a.reference().identity().equals(verified.identity())
                || !a.getOrderId().equals(verified.orderId()))throw invalid();
        var old=events.findByProviderCodeAndProviderAccountIdAndEnvironmentAndEventKeyHash(
                verified.identity().code(),verified.identity().providerAccountId(),verified.identity().environment(),eventHash);
        if(old.isPresent())return matching(old.get(),a.getId(),payloadHash);
        var e=events.saveAndFlush(PaymentWebhookEvent.receive(a.getId(),verified.identity().code(),
                verified.identity().merchant(),verified.identity().providerAccountId(),verified.identity().environment(),eventHash,payloadHash,clock.instant()));
        // Receipt/ACK is durable before any gateway lookup. No untrusted event state is applied.
        var j=jobs.findByAttemptId(a.getId()).orElseGet(()->jobs.save(PaymentReconciliationJob.create(a.getId(),clock.instant())));
        if(p.getStatus()!=PaymentStatus.PAID && p.getStatus()!=PaymentStatus.FAILED && p.getStatus()!=PaymentStatus.CANCELED)
            j.pending(clock.instant(),PaymentProcessingFailure.NONE);
        return new Receipt(e.getId(),false);
    }

    public Receipt duplicate(PaymentGateway.VerifiedEvent verified,String eventHash,String digest,Long attemptId) {
        var e=events.findByProviderCodeAndProviderAccountIdAndEnvironmentAndEventKeyHash(
                verified.identity().code(),verified.identity().providerAccountId(),verified.identity().environment(),eventHash)
                .orElseThrow(this::invalid);
        return matching(e,attemptId,digest);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Claim claim(Long eventId) {
        var e=events.lockById(eventId).orElseThrow(this::invalid);em.refresh(e,LockModeType.PESSIMISTIC_WRITE);
        String token=UUID.randomUUID().toString();
        boolean run=e.claim(token,clock.instant(),retryPolicy.lease(),retryPolicy.maxAttempts());
        return new Claim(e.getId(),e.getAttemptId(),token,run,e.getAttemptCount());
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean finish(Claim claim,PaymentWebhookEvent.Outcome outcome,String failureCode) {
        var e=events.lockById(claim.id()).orElseThrow(this::invalid);em.refresh(e,LockModeType.PESSIMISTIC_WRITE);
        return e.finish(claim.token(),clock.instant(),outcome,failureCode,retryPolicy.maxAttempts(),
                retryPolicy.delayAfterAttempt(claim.attemptCount()));
    }
    public PaymentGateway.VerifiedEvent resolvePortOne(PaymentGateway.VerifiedEvent e) {
        if(!"PORTONE_V2".equals(e.identity().code()))throw invalid();
        var a=attempts.findByAttemptKey(e.attemptKey()).orElseThrow(this::invalid);
        if(!gateway.isIdentityCompatible(a.reference().identity())
                || !a.reference().identity().code().equals(e.identity().code())
                || a.reference().identity().environment()!=e.identity().environment())throw invalid();
        return new PaymentGateway.VerifiedEvent(a.reference().identity(),e.eventId(),e.attemptKey(),a.getOrderId());
    }
    public Long attemptId(String key) {return attempts.findByAttemptKey(key).orElseThrow(this::invalid).getId();}
    private Receipt matching(PaymentWebhookEvent e,Long attempt,String hash) {
        if(!e.getAttemptId().equals(attempt) || !e.getPayloadHash().equals(hash))
            throw new CustomException(ErrorCode.PAYMENT_EVENT_CONFLICT);
        return new Receipt(e.getId(),true);
    }
    private CustomException invalid() {return new CustomException(ErrorCode.PAYMENT_EVENT_INVALID);}
}
