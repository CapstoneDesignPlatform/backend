package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.payment.gateway.PaymentGateway;
import com.capdi.backend.domain.payment.entity.PaymentAttemptStatus;
import com.capdi.backend.domain.payment.entity.PaymentProcessingFailure;
import com.capdi.backend.domain.payment.entity.PaymentWebhookEvent;
import com.capdi.backend.domain.payment.util.PaymentProcessingKeys;
import com.capdi.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Internal service only: the optional PortOne receiver only ACKs after signature verification and durable receipt. */
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class PaymentWebhookService {
    private final PaymentGateway gateway;
    private final PaymentWebhookPersistenceService persistence;
    private final PaymentExecutionService execution;
    private final PaymentWebhookRetryPolicy retryPolicy;

    public PaymentWebhookPersistenceService.Receipt receive(Map<String,List<String>> headers,byte[] body) {
        var identity=PaymentGatewaySafety.require(gateway,PaymentGateway.Capability.VERIFY_WEBHOOK);
        if(body==null || body.length==0 || body.length>65536)throw invalid();
        PaymentGateway.VerifiedEvent event;
        try {event=gateway.verifyWebhook(headers,body);}
        catch(com.capdi.backend.domain.payment.gateway.portone.PortOneIgnoredWebhookException ignored) {
            return new PaymentWebhookPersistenceService.Receipt(null,false);
        }
        catch(RuntimeException ignored) {throw invalid();}
        if(event==null || !identity.equals(event.identity()) || event.eventId()==null || event.eventId().isBlank()
                || event.eventId().length()>256 || event.attemptKey()==null)throw invalid();
        if(event.orderId()==null)event=persistence.resolvePortOne(event);
        String key=PaymentProcessingKeys.digest(event.eventId()),digest=PaymentProcessingKeys.digest(body);
        try {return persistence.record(event,key,digest);}
        catch(DataIntegrityViolationException collision) {
            return persistence.duplicate(event,key,digest,persistence.attemptId(event.attemptKey()));
        }
    }
    public void process(Long eventId) {
        var claim=persistence.claim(eventId);
        if(!claim.run())return;
        PaymentWebhookEvent.Outcome outcome;
        String failureCode=null;
        try {
            var result=execution.reconcileInternal(claim.attempt());
            PaymentProcessingFailure failure=result.getFailure();
            if(result.getStatus()!=PaymentAttemptStatus.UNKNOWN && failure==PaymentProcessingFailure.NONE) {
                outcome=PaymentWebhookEvent.Outcome.SUCCESS;
            } else if (failure==null || failure==PaymentProcessingFailure.NONE) {
                outcome=PaymentWebhookEvent.Outcome.TRANSIENT_FAILURE;
                failureCode="RECONCILIATION_PENDING";
            } else {
                var classification=retryPolicy.classify(failure);
                outcome=classification==PaymentWebhookRetryPolicy.PaymentWebhookEventFailure.TRANSIENT
                        ?PaymentWebhookEvent.Outcome.TRANSIENT_FAILURE:PaymentWebhookEvent.Outcome.PERMANENT_FAILURE;
                failureCode=failure.name();
            }
        } catch(RuntimeException ignored) {
            // Persist only a fixed safe failure code. Exception messages/provider bodies may contain secrets.
            outcome=PaymentWebhookEvent.Outcome.TRANSIENT_FAILURE;
            failureCode=PaymentProcessingFailure.PROCESS_INTERRUPTED.name();
        }
        persistence.finish(claim,outcome,failureCode);
    }
    private CustomException invalid() {return new CustomException(ErrorCode.PAYMENT_EVENT_INVALID);}
}
