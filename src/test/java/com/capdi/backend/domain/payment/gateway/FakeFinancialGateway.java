package com.capdi.backend.domain.payment.gateway;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Test sources only. Never contacts a provider or claims real KYC. */
public class FakeFinancialGateway implements FinancialGateway {
 public final AtomicInteger submits=new AtomicInteger(),lookups=new AtomicInteger();
 public volatile Function<Command,Evidence> submitFunction,lookupFunction;
 public volatile Identity providerIdentity;public volatile boolean enabled;
 public FakeFinancialGateway(){reset();}
 public void reset(){enabled=true;providerIdentity=new Identity("TEST_FAKE","synthetic-merchant","synthetic-store","TEST");submits.set(0);lookups.set(0);submitFunction=c->evidence(c,State.SUCCEEDED);lookupFunction=c->evidence(c,State.SUCCEEDED);}
 public Identity identity(){return providerIdentity;}
 public boolean canSubmit(Kind k){return enabled;}public boolean canLookup(Kind k){return enabled;}
 public Evidence evidence(Command c,State state){
  String id=c.kind()==Kind.PARTNER?c.payload().path("partnerId").asText():"synthetic-"+c.reference();
  return new Evidence(c.reference(),id,state,c.kind()==Kind.PARTNER?null:c.payload().path("amount").decimalValue(),
    "KRW",c.kind()==Kind.PARTNER,c.kind()==Kind.PARTNER,c.kind()==Kind.TAX_INVOICE?NtsState.SUCCEEDED:NtsState.NOT_SENT);
 }
 public Evidence submit(Command c){outside();submits.incrementAndGet();return submitFunction.apply(c);}
 public Evidence lookup(Command c,String id){outside();lookups.incrementAndGet();return lookupFunction.apply(c);}
 private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new AssertionError("Financial I/O inside transaction");}
}
