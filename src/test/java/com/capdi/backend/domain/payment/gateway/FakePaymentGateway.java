package com.capdi.backend.domain.payment.gateway;

import com.capdi.backend.domain.payment.entity.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** Test sources only. This class is not packaged in the production JAR. */
public class FakePaymentGateway implements PaymentGateway {
    public volatile boolean enabled=true;
    public final Set<Capability> capabilities=ConcurrentHashMap.newKeySet();
    public final AtomicInteger prepares=new AtomicInteger(),confirms=new AtomicInteger(),fetches=new AtomicInteger(),verifications=new AtomicInteger();
    public final Map<String,Observation> ledger=new ConcurrentHashMap<>();
    public volatile Function<Reference,Observation> prepareFunction,confirmFunction,fetchFunction;
    public FakePaymentGateway(){reset();}
    public void reset() {
        enabled=true;capabilities.clear();capabilities.addAll(EnumSet.allOf(Capability.class));
        prepares.set(0);confirms.set(0);fetches.set(0);verifications.set(0);ledger.clear();
        prepareFunction=r->publish(r,State.PREPARED);
        confirmFunction=r->publish(r,State.PAID);
        fetchFunction=r->ledger.getOrDefault(r.attemptKey(),observation(r,State.UNKNOWN));
    }
    public Identity identity(){return new Identity("TEST_FAKE","synthetic-merchant",PaymentEnvironment.TEST);}
    public boolean enabled(){return enabled;}
    public boolean supports(Capability c){return capabilities.contains(c);}
    public Observation observation(Reference r,State state) {
        String tx=state==State.PREPARED?null:"synthetic-tx-"+r.attemptKey();
        return new Observation(identity(),r.orderId(),r.amount(),r.currency(),tx,state.name(),state,true);
    }
    public Observation publish(Reference r,State state) {var o=observation(r,state);ledger.put(r.attemptKey(),o);return o;}
    private void outsideTransaction() {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new AssertionError("Gateway IO inside DB transaction");
    }
    public Observation prepare(Reference r){outsideTransaction();prepares.incrementAndGet();return prepareFunction.apply(r);}
    public Observation confirm(Reference r,String ref){outsideTransaction();confirms.incrementAndGet();return confirmFunction.apply(r);}
    public Observation fetch(Reference r){outsideTransaction();fetches.incrementAndGet();return fetchFunction.apply(r);}
    public VerifiedEvent verifyWebhook(Map<String,List<String>> headers,byte[] raw) {
        outsideTransaction();verifications.incrementAndGet();
        if(!List.of("SYNTHETIC_SIGNATURE").equals(headers.get("X-Test-Signature")))throw new GatewayException(PaymentProcessingFailure.PROVIDER_ERROR);
        String[] parts=new String(raw,StandardCharsets.UTF_8).split("\\|",-1);
        if(parts.length<3)throw new GatewayException(PaymentProcessingFailure.PROVIDER_ERROR);
        return new VerifiedEvent(identity(),parts[0],parts[1],parts[2]);
    }
}
