package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.PaymentGateway;
import com.capdi.backend.global.exception.*;

final class PaymentGatewaySafety {
    private PaymentGatewaySafety() { }
    static PaymentGateway.Identity require(PaymentGateway gateway,PaymentGateway.Capability capability) {
        var id=gateway.identity();
        if(!gateway.enabled() || !gateway.supports(capability)
                || (capability==PaymentGateway.Capability.PREPARE || capability==PaymentGateway.Capability.CONFIRM)
                && !gateway.supports(PaymentGateway.Capability.LOOKUP)
                || id==null || id.environment()==null
                || id.environment()==PaymentEnvironment.DISABLED || id.code()==null || !id.code().matches("[A-Z0-9_-]{1,40}")
                || "DISABLED".equals(id.code()) || id.merchant()==null || !id.merchant().matches("[A-Za-z0-9._:-]{1,100}"))
            throw new CustomException(ErrorCode.PAYMENT_GATEWAY_DISABLED);
        return id;
    }
    static boolean matches(PaymentAttempt a,PaymentGateway.Observation o) {
        if(o==null || !o.authoritative() || o.state()==null || !a.reference().identity().equals(o.identity())
                || !a.getOrderId().equals(o.orderId()) || o.amount()==null || a.getChargeAmount().compareTo(o.amount())!=0
                || !a.getCurrency().equals(o.currency()) || o.rawStatus()==null || !o.rawStatus().matches("[A-Za-z0-9_.:-]{1,80}"))
            return false;
        if(o.transactionId()!=null && !o.transactionId().matches("[A-Za-z0-9_.:-]{1,200}"))return false;
        if(a.getProviderTransactionId()!=null && !a.getProviderTransactionId().equals(o.transactionId()))return false;
        if((o.state()==PaymentGateway.State.PAID || o.state()==PaymentGateway.State.WAITING_DEPOSIT) && o.transactionId()==null)return false;
        return o.state()!=PaymentGateway.State.WAITING_DEPOSIT || a.getMethod()==PaymentMethod.VIRTUAL_ACCOUNT;
    }
}
