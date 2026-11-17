package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.gateway.PayoutProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class InternalPayoutService {
 private final InternalPayoutPersistenceService persistence;private final PayoutProvider provider;
 public InternalPayoutPersistenceService.View execute(Long admin,Long id,boolean lookup){
  return call(persistence.claim(admin,id,lookup),lookup);
 }
 public int runDue(){int count=0;for(Long id:persistence.dueIds())try{call(persistence.claimWorker(id),true);count++;}catch(RuntimeException ignored){}return count;}
 private InternalPayoutPersistenceService.View call(InternalPayoutPersistenceService.Run run,boolean lookup){
  PayoutProvider.Result result;
  try{result=lookup?provider.lookup(run.request().payoutIdentity(),run.externalId()):provider.request(run.request());}
  catch(RuntimeException ex){result=null;}
  try{return persistence.complete(run,result);}catch(org.springframework.dao.DataAccessException ex){return persistence.complete(run,null);}
 }
}
