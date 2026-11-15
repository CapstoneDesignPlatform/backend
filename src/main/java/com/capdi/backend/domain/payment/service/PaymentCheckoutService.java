package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.dto.PaymentCheckoutResponse;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.PaymentGateway;
import com.capdi.backend.domain.payment.gateway.portone.PortOneV2Gateway;
import com.capdi.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class PaymentCheckoutService {
    private final PaymentExecutionPersistenceService persistence;
    private final PaymentGateway gateway;
    public PaymentCheckoutResponse read(Long user,Long payment,Long attempt) {
        var response=persistence.read(user,payment,attempt); // JWT role is not sufficient: DB role/owner checked.
        if(response.getStatus()!=PaymentAttemptStatus.PREPARED || response.getPaymentStatus()!=PaymentStatus.PROCESSING)
            throw new CustomException(ErrorCode.PAYMENT_EXECUTION_CONFLICT);
        if(!(gateway instanceof PortOneV2Gateway portone) || !portone.enabled())
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_DISABLED);
        var run=persistence.locate(attempt);
        if(!run.user().equals(user) || !run.payment().equals(payment))throw new CustomException(ErrorCode.FORBIDDEN);
        return portone.checkout(run.reference());
    }
}
