package com.capdi.backend.domain.payment.gateway;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.transaction.support.TransactionSynchronizationManager;
public class FakePayoutProvider implements PayoutProvider {
 public final AtomicInteger requests=new AtomicInteger(),lookups=new AtomicInteger();
 public volatile State next=State.SUCCEEDED;public volatile BigDecimal amountOverride;public volatile String providerCode="TEST_ONLY";private Request last;
 public Identity identity(){return new Identity(providerCode,"TEST");}
 public boolean enabled(){return true;}
 public Result request(Request request){outside();requests.incrementAndGet();last=request;return result();}
 public Result lookup(String identity,String external){outside();lookups.incrementAndGet();return result();}
 private Result result(){return new Result(last.payoutIdentity(),"test-payout-"+last.payoutIdentity(),(amountOverride==null?last.amount():amountOverride),last.currency(),next,true);}
 private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new AssertionError("Provider called inside transaction");}
 public void reset(){requests.set(0);lookups.set(0);next=State.SUCCEEDED;last=null;amountOverride=null;providerCode="TEST_ONLY";}
}
