package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.PaymentGateway;
import com.capdi.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.function.Supplier;

@Service @RequiredArgsConstructor
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class PaymentExecutionService {
    private final PaymentExecutionPersistenceService persistence;
    private final PaymentGateway gateway;
    public PaymentAttemptResponse prepare(Long user,Long payment,String key,PaymentMethod method) {
        var run=persistence.prepare(user,payment,key,method);
        return execute(run,()->gateway.prepare(run.reference()));
    }
    public PaymentAttemptResponse confirm(Long user,Long payment,Long attempt,String reference) {
        var run=persistence.confirm(user,payment,attempt,reference);
        return execute(run,()->gateway.confirm(run.reference(),reference));
    }
    public PaymentAttemptResponse reconcile(Long user,Long payment,Long attempt) {
        var run=persistence.lookup(user,payment,attempt);
        return execute(run,()->gateway.fetch(run.reference()));
    }
    public PaymentAttemptResponse reconcileInternal(Long attempt) {
        var ref=persistence.locate(attempt);return reconcile(ref.user(),ref.payment(),ref.attempt());
    }
    public PaymentAttemptResponse read(Long user,Long payment,Long attempt) {return persistence.read(user,payment,attempt);}
    private PaymentAttemptResponse execute(PaymentExecutionPersistenceService.Execution run,Supplier<PaymentGateway.Observation> call) {
        if(!run.run())return run.response();
        PaymentGateway.Observation observation;
        try {observation=call.get();}
        catch(PaymentGateway.GatewayException e) {return fail(run,e.failure()==null?PaymentProcessingFailure.PROVIDER_ERROR:e.failure());}
        catch(RuntimeException e) {return fail(run,PaymentProcessingFailure.PROVIDER_ERROR);}
        try {return persistence.complete(run,observation);}
        catch(CustomException e) {throw e;}
        catch(DataIntegrityViolationException e) {return fail(run,PaymentProcessingFailure.TRANSACTION_CONFLICT);}
        catch(RuntimeException e) {return fail(run,PaymentProcessingFailure.STORAGE_FAILURE);}
    }
    private PaymentAttemptResponse fail(PaymentExecutionPersistenceService.Execution run,PaymentProcessingFailure reason) {
        try {return persistence.failed(run,reason);}
        catch(CustomException e) {throw e;}
        catch(RuntimeException e) {throw new CustomException(ErrorCode.PAYMENT_EXECUTION_STORAGE_FAILED);}
    }
}
