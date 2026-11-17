package com.capdi.backend.domain.payment.gateway.portone;

import com.capdi.backend.domain.payment.gateway.PaymentGateway;
import com.capdi.backend.domain.payment.entity.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;

/** Standard browser-initiated V2 flow. confirm performs GET verification, never POST /confirm. */
public final class PortOneV2Gateway implements PaymentGateway {
    private final PortOneSettings settings;
    private final PortOneTransport transport;
    private final ObjectMapper mapper;
    private final PortOneWebhookVerifier verifier;
    public PortOneV2Gateway(PortOneSettings settings,PortOneTransport transport,ObjectMapper mapper,Clock clock) {
        this.settings=settings;this.transport=transport;
        this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.verifier=new PortOneWebhookVerifier(settings,clock,this.mapper);
    }
    public Identity identity(){return enabled()?new Identity("PORTONE_V2",settings.merchantId(),PaymentEnvironment.SANDBOX)
            :new Identity("DISABLED","DISABLED",PaymentEnvironment.DISABLED);}
    public boolean enabled(){return settings.enabled();}
    public boolean supports(Capability c){
        return enabled() && (c==Capability.PREPARE || c==Capability.CONFIRM || c==Capability.LOOKUP
                || c==Capability.VERIFY_WEBHOOK && verifier.configured());
    }
    public static String paymentId(Reference r){return "capdi_"+r.attemptKey();}
    private void check(Reference r) {
        if(!enabled())throw new GatewayException(PaymentProcessingFailure.DISABLED);
        if(r==null || !identity().equals(r.identity()) || !PortOneSettings.identifier(paymentId(r))
                || r.amount()==null || r.amount().signum()<=0 || r.amount().stripTrailingZeros().scale()>0
                || !"KRW".equals(r.currency()) || r.method()==null)
            throw new GatewayException(PaymentProcessingFailure.RESPONSE_MISMATCH);
    }
    public com.capdi.backend.domain.payment.dto.PaymentCheckoutResponse checkout(Reference r) {
        check(r);
        return com.capdi.backend.domain.payment.dto.PaymentCheckoutResponse.builder()
            .storeId(settings.storeId()).channelKey(settings.channelKey()).paymentId(paymentId(r))
            .orderName("CAPDI diagnosis service").totalAmount(r.amount()).currency("CURRENCY_KRW")
            .payMethod(r.method()).environment("SANDBOX").build();
    }
    public Observation prepare(Reference r) {
        check(r);
        // Only local preparation. No PG request, no fabricated transaction or checkout URL.
        return new Observation(identity(),r.orderId(),r.amount(),r.currency(),null,"LOCAL_PREPARED",State.PREPARED,true);
    }
    public Observation confirm(Reference r,String browserReference) {
        check(r);
        if(!paymentId(r).equals(browserReference))throw new GatewayException(PaymentProcessingFailure.RESPONSE_MISMATCH);
        return fetch(r);
    }
    public Observation fetch(Reference r) {
        check(r);
        var reply=transport.get(paymentId(r),settings.storeId(),settings.apiSecret(),settings.timeout());
        if(reply.status()==404)return new Observation(identity(),r.orderId(),r.amount(),r.currency(),null,"NOT_FOUND",State.UNKNOWN,false);
        if(reply.status()!=200)throw new GatewayException(PaymentProcessingFailure.PROVIDER_ERROR);
        try {
            if(reply.body()==null || reply.body().length>65536)throw new IllegalArgumentException();
            JsonNode n=mapper.readTree(reply.body());
            String status=text(n,"status");
            JsonNode amount=n.path("amount").path("total");
            if(!paymentId(r).equals(text(n,"id")) || !settings.storeId().equals(text(n,"storeId"))
                    || !settings.merchantId().equals(text(n,"merchantId")) || !"V2".equals(text(n,"version"))
                    || !"KRW".equals(text(n,"currency")) || !amount.isIntegralNumber()
                    || amount.decimalValue().compareTo(r.amount())!=0
                    || !r.method().name().equals(text(n.path("method"),"type"))
                    || !"TEST".equals(text(n.path("channel"),"type"))
                    || !settings.channelKey().equals(text(n.path("channel"),"key")))
                throw new IllegalArgumentException();
            State state=switch(status) {
                case "READY" -> State.PREPARED;
                case "PENDING","PAY_PENDING" -> State.REQUESTED;
                case "VIRTUAL_ACCOUNT_ISSUED" -> State.WAITING_DEPOSIT;
                case "PAID" -> State.PAID;
                case "FAILED" -> State.FAILED;
                case "CANCELLED" -> State.CANCELED;
                default -> State.UNKNOWN; // includes partial cancellations: separate refund aggregate required.
            };
            String tx=n.path("transactionId").isTextual()?n.path("transactionId").textValue():null;
            if(tx!=null && !tx.matches("[A-Za-z0-9_.:-]{1,200}"))throw new IllegalArgumentException();
            if((state==State.PAID || state==State.WAITING_DEPOSIT) && tx==null)throw new IllegalArgumentException();
            if(state==State.WAITING_DEPOSIT && r.method()!=PaymentMethod.VIRTUAL_ACCOUNT)throw new IllegalArgumentException();
            return new Observation(identity(),r.orderId(),r.amount(),r.currency(),tx,status,state,state!=State.UNKNOWN);
        } catch(Exception e){throw new GatewayException(PaymentProcessingFailure.RESPONSE_MISMATCH);}
    }
    private static String text(JsonNode n,String field) {
        JsonNode v=n.path(field);if(!v.isTextual())throw new IllegalArgumentException();return v.textValue();
    }
    public VerifiedEvent verifyWebhook(Map<String,List<String>> headers,byte[] body) {
        if(!supports(Capability.VERIFY_WEBHOOK))throw new GatewayException(PaymentProcessingFailure.DISABLED);
        return verifier.verify(identity(),headers,body);
    }
}
