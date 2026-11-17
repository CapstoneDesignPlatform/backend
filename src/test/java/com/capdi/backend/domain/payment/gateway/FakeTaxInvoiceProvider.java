package com.capdi.backend.domain.payment.gateway;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
public class FakeTaxInvoiceProvider implements TaxInvoiceProvider {
 public final AtomicInteger requests=new AtomicInteger(),lookups=new AtomicInteger();
 private final ConcurrentMap<String,Request> snapshots=new ConcurrentHashMap<>();
 public volatile IssuanceState next=IssuanceState.ISSUED;public volatile TransmissionState transmission=TransmissionState.SUCCEEDED;
 public volatile boolean timeout,wrongAmount;public volatile String code="TEST_TAX";
 public Identity identity(){return new Identity(code,"TEST");}public boolean enabled(){return true;}
 public Result request(Request r){outside();requests.incrementAndGet();snapshots.put(r.documentIdentity(),r);if(timeout)throw new IllegalStateException("Synthetic timeout");return result(r);}
 public Result lookup(String identity,String external){outside();lookups.incrementAndGet();return result(snapshots.get(identity));}
 private Result result(Request r){return new Result(r.documentIdentity(),"synthetic-tax-"+r.documentIdentity(),next,transmission,true,true,r.snapshotHash(),r.supply(),r.vat(),wrongAmount?java.math.BigDecimal.ONE:r.total(),r.currency());}
 public void reset(){requests.set(0);lookups.set(0);snapshots.clear();next=IssuanceState.ISSUED;transmission=TransmissionState.SUCCEEDED;timeout=false;wrongAmount=false;code="TEST_TAX";}
 private void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new AssertionError("Tax provider inside transaction");}
}
