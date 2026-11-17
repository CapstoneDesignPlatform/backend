package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.gateway.TaxInvoiceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class InternalTaxExecutionService {
 private final InternalTaxExecutionPersistenceService persistence;private final TaxInvoiceProvider provider;
 public InternalTaxExecutionPersistenceService.View execute(Long admin,Long id,boolean lookup){return call(persistence.claimAdmin(admin,id,lookup),lookup);}
 public int runDue(){int count=0;for(Long id:persistence.dueIds())try{call(persistence.claimWorker(id),true);count++;}catch(RuntimeException ignored){}return count;}
 private InternalTaxExecutionPersistenceService.View call(InternalTaxExecutionPersistenceService.Run run,boolean lookup){
  TaxInvoiceProvider.Result proof;try{proof=lookup?provider.lookup(run.request().documentIdentity(),run.externalId()):provider.request(run.request());}catch(RuntimeException ex){proof=null;}
  try{return persistence.complete(run,proof);}catch(org.springframework.dao.DataAccessException ex){return persistence.complete(run,null);}
 }
}
