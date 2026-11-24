package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.gateway.PayoutProvider;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class InternalPayoutService {
 private final InternalPayoutPersistenceService persistence;private final PayoutProvider provider;private final FinancialWorkflowService canonical;
 public InternalPayoutPersistenceService.View execute(Long admin,Long id,boolean lookup){
  if(persistence.isCompatibilityId(id)){
   Long operation=persistence.compatibilityOperationId(admin,id);
   if(operation==null)return persistence.readCompatibility(admin,id);
   if(lookup)canonical.reconcile(admin,operation);else canonical.execute(admin,operation);
   return persistence.readCompatibility(admin,id);
  }
  // Historical InternalPayout rows are never submitted again. Only their existing dispatched
  // records may use the legacy lookup-only recovery path.
  if(!lookup)persistence.rejectLegacyExecution(admin,id);
  return call(persistence.claim(admin,id,lookup),lookup);
 }
 public int runDue(){int count=0;for(Long id:persistence.dueIds())try{call(persistence.claimWorker(id),true);count++;}catch(RuntimeException ignored){}return count;}
 private InternalPayoutPersistenceService.View call(InternalPayoutPersistenceService.Run run,boolean lookup){
  if(!lookup)throw new CustomException(ErrorCode.PAYMENT_EXECUTION_CONFLICT);
  PayoutProvider.Result result;
  try{result=provider.lookup(run.request().payoutIdentity(),run.externalId());}
  catch(RuntimeException ex){result=null;}
  try{return persistence.complete(run,result);}catch(org.springframework.dao.DataAccessException ex){return persistence.complete(run,null);}
 }
}
