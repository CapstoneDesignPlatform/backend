package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.dto.FinancialWorkflowResponse;
import com.capdi.backend.domain.payment.gateway.*;
import com.capdi.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class FinancialWorkflowService {
 private final FinancialWorkflowPersistenceService persistence;private final FinancialGateway gateway;
 public FinancialWorkflowResponse execute(Long admin,Long id){return call(persistence.claimAdmin(admin,id,false),false,admin);}
 public FinancialWorkflowResponse reconcile(Long admin,Long id){return call(persistence.claimAdmin(admin,id,true),true,admin);}
 public int runDue(){
  int count=0;
  for(Long id:persistence.due())try {var run=persistence.claimWorker(id);if(run.execute()){call(run,true,null);count++;}}catch(RuntimeException ignored){}
  return count;
 }
 public int runDueTaxInvoices(){
  int count=0;
  for(Long id:persistence.due(FinancialGateway.Kind.TAX_INVOICE))try {var run=persistence.claimWorker(id);if(run.execute()){call(run,true,null);count++;}}catch(RuntimeException ignored){}
  return count;
 }
 private FinancialWorkflowResponse call(FinancialWorkflowPersistenceService.Run run,boolean lookup,Long actor) {
  if(!run.execute())return actor==null?null:persistence.readAdmin(actor,run.operation());
  FinancialGateway.Evidence result;
  try {result=lookup?gateway.lookup(run.command(),run.externalId()):gateway.submit(run.command());}
  catch(FinancialGatewayException e){return persistence.uncertain(run,e.failure().name());}
  catch(RuntimeException e){return persistence.uncertain(run,"PROVIDER_ERROR");}
  try {return persistence.complete(run,result);}
  catch(CustomException e){throw e;}
  catch(DataIntegrityViolationException e){return persistence.uncertain(run,"PERSISTENCE_CONFLICT");}
  catch(RuntimeException e){return persistence.uncertain(run,"PERSISTENCE_FAILURE");}
 }
}
