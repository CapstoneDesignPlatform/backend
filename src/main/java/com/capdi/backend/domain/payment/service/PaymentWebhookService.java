package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.payment.gateway.PaymentGateway;
import com.capdi.backend.domain.payment.entity.PaymentAttemptStatus;
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
        boolean done=false;
        try {
            var result=execution.reconcileInternal(claim.attempt());
            done=result.getStatus()!=PaymentAttemptStatus.UNKNOWN && result.getFailure()==com.capdi.backend.domain.payment.entity.PaymentProcessingFailure.NONE;
        } catch(RuntimeException ignored) {
            // Preserve the inbox; a later explicitly invoked worker can retry LOOKUP, never a charge.
        } finally {persistence.finish(claim,done);}
    }
    private CustomException invalid() {return new CustomException(ErrorCode.PAYMENT_EVENT_INVALID);}
}
