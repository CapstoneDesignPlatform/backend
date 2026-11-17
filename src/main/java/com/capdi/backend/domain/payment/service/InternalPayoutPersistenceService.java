package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.*;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.domain.payment.config.FinancialPolicy;
import com.capdi.backend.global.exception.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import static com.capdi.backend.domain.payment.entity.FinancialLedgerEntry.Bucket.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class InternalPayoutPersistenceService {
 private final PaymentSourceRepository sources;private final PaymentRepository payments;private final SettlementBalanceRepository balances;
 private final SettlementReviewRepository reviews;private final ExpertSettlementProfileRepository profiles;private final InternalPayoutRepository payouts;
 private final FinancialLedgerEntryRepository ledger;private final PaymentRefundRequestRepository refunds;
 private final PayoutProvider provider;private final FinancialPolicy policy;private final EntityManager em;private final Clock clock;
 private final InternalSettlementService settlements;private final FinancialAuditEventRepository auditEvents;private final FinancialSnapshotGuard snapshotGuard;
 private final com.capdi.backend.domain.expert.repository.BusinessRegistrationInfoRepository business;
 private final com.capdi.backend.domain.expert.repository.ExpertFileRepository files;
 public record View(Long id,Long payment_id,Long review_id,BigDecimal amount,InternalPayout.State state,
  boolean dispatched,long version,boolean real_transfer_confirmed){}
 public record Run(Long id,String token,PayoutProvider.Request request,String externalId,boolean lookup){}
 @Transactional public View reserve(Long admin,Long payment,Long review){
  role(admin);var p=lock(payment);var previous=payouts.findByReviewId(review);
  if(previous.isPresent()){if(!previous.get().getPaymentId().equals(payment))throw conflict();return view(previous.get());}
  enabled();var b=balance(payment);
  var r=reviews.findById(review).orElseThrow(this::missing);em.refresh(r);
  var profile=latestProfile(p.getExpertProfileId());
  var history=settlements.history(admin,payment);
  if(p.getStatus()!=PaymentStatus.PAID || !r.getPaymentId().equals(payment) || r.getState()!=SettlementReview.State.APPROVED
   || !r.current(b.getVersion(),profile.getRevision()) || history.isEmpty() || !history.getFirst().id().equals(review)
   || !history.getFirst().snapshot_current() || !snapshotGuard.profileApproved(profile,true) || pendingRefund(payment))throw conflict();
  try{b.reserve(r.getAmount());}catch(IllegalArgumentException|IllegalStateException ex){throw conflict();}
  balances.flush();
  var payout=payouts.saveAndFlush(InternalPayout.reserve(payment,review,profile.getId(),b.getVersion(),r.getAmount(),provider.identity().code(),provider.identity().environment()));
  post(payout,"reserve",Map.of(EXPERT_PAYABLE,r.getAmount().negate(),PAYOUT_RESERVED,r.getAmount()));audit(payout,admin,"RESERVED","reserve:"+payout.getId());return view(payout);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public Run claim(Long admin,Long id,boolean lookup){
  role(admin);return claimInternal(admin,id,lookup,false);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW) public Run claimWorker(Long id){return claimInternal(null,id,true,true);}
 private Run claimInternal(Long admin,Long id,boolean lookup,boolean worker){
  var old=payouts.findById(id).orElseThrow(this::missing);lock(old.getPaymentId());em.refresh(old);if(worker && !old.due(clock.instant()))throw conflict();
  if(lookup){if(!provider.enabled())throw new CustomException(ErrorCode.PAYMENT_FINANCIAL_POLICY_UNCONFIRMED);}else enabled();
  if(!old.getProviderCode().equals(provider.identity().code()) || !old.getProviderEnvironment().equals(provider.identity().environment()))throw conflict();
  var b=balance(old.getPaymentId());var profile=profiles.findById(old.getProfileId()).orElseThrow(this::missing);
  if(!lookup){
   var latest=latestProfile(profile.getExpertProfileId());
   var history=settlements.history(admin,old.getPaymentId());
   if(!latest.getId().equals(profile.getId()) || b.getVersion()!=old.getBalanceVersion() || pendingRefund(old.getPaymentId())
    || history.isEmpty() || !history.getFirst().id().equals(old.getReviewId())
    || history.getFirst().state()!=SettlementReview.State.APPROVED || !profileCurrent(profile) || !snapshotGuard.profileApproved(profile,true) || !Objects.equals(reviews.findById(old.getReviewId()).orElseThrow(this::missing).getProfileReviewId(),snapshotGuard.latestReviewId(profile,true)))throw conflict();
  }
  if(lookup && !worker && old.getLookupCount()>=8){old.allowManualLookup(clock.instant());audit(old,admin,"MANUAL_LOOKUP_RESET","reset:"+old.getId()+":"+old.getVersion());}
  String token;try{token=old.claim(lookup,clock.instant());}catch(IllegalStateException ex){throw conflict();}
  payouts.flush();audit(old,admin,lookup?"LOOKUP_CLAIM":"PAYOUT_CLAIM","claim:"+token);return new Run(id,token,new PayoutProvider.Request(old.getIdentity(),profile.getTokenReference(),old.getAmount(),"KRW"),old.getExternalId(),lookup);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public View complete(Run run,PayoutProvider.Result evidence){
  var old=payouts.findById(run.id()).orElseThrow(this::missing);lock(old.getPaymentId());em.refresh(old);
  if(!old.owns(run.token()))throw conflict();
  var b=balance(old.getPaymentId());
  boolean valid=evidence!=null && evidence.authoritative() && evidence.state()!=null && old.getIdentity().equals(evidence.payoutIdentity())
   && "KRW".equals(evidence.currency()) && evidence.amount()!=null && old.getAmount().compareTo(evidence.amount())==0
   && evidence.providerPayoutId()!=null && evidence.providerPayoutId().matches("[A-Za-z0-9_.:-]{1,200}")
   && (old.getExternalId()==null || old.getExternalId().equals(evidence.providerPayoutId()));
  InternalPayout.State next=valid?switch(evidence.state()){
   case SUCCEEDED->InternalPayout.State.SUCCEEDED;case FAILED->InternalPayout.State.FAILED;case CANCELED->InternalPayout.State.CANCELED;
   case REQUESTED->InternalPayout.State.REQUESTED;default->InternalPayout.State.UNKNOWN;
  }:InternalPayout.State.UNKNOWN;
  if(next==InternalPayout.State.SUCCEEDED){
   BigDecimal recovery=b.getRecoverable();b.paid(old.getAmount());
   post(old,"paid",Map.of(PAYOUT_RESERVED,old.getAmount().negate(),PAYOUT_PAID,old.getAmount()));
   var delta=b.getRecoverable().subtract(recovery);
   if(delta.signum()!=0)post(old,"recovery",Map.of(RECOVERY_RECEIVABLE,delta,EXPERT_PAYABLE,delta.negate()));
  }else if(next==InternalPayout.State.FAILED || next==InternalPayout.State.CANCELED){
   b.release(old.getAmount());post(old,"release",Map.of(PAYOUT_RESERVED,old.getAmount().negate(),EXPERT_PAYABLE,old.getAmount()));
  }
  old.observe(run.token(),next,valid?evidence.providerPayoutId():null,clock.instant());payouts.flush();audit(old,null,valid?"PROVIDER_OBSERVED":"UNCONFIRMED_EVIDENCE","result:"+run.token());return view(old);
 }
 @Transactional public View cancel(Long admin,Long id){
  role(admin);var p=payouts.findById(id).orElseThrow(this::missing);lock(p.getPaymentId());em.refresh(p);
  if(p.getState()==InternalPayout.State.CANCELED && !p.isDispatched())return view(p);
  try{p.cancel();}catch(IllegalStateException ex){throw conflict();}
  var b=balance(p.getPaymentId());b.release(p.getAmount());post(p,"release",Map.of(PAYOUT_RESERVED,p.getAmount().negate(),EXPERT_PAYABLE,p.getAmount()));audit(p,admin,"RESERVATION_CANCELED","cancel:"+p.getId());return view(p);
 }
 public List<View> list(Long user,Long payment,boolean admin){
  if(admin)readRole(user);else if(!"EXPERT".equals(sources.userType(user,false).orElse("")))throw forbidden();
  var p=payments.findById(payment).orElseThrow(this::missing);
  if(!admin && !p.getExpertProfileId().equals(sources.expertProfileId(user).orElse(null)))throw forbidden();
  return payouts.findByPaymentIdOrderByIdDesc(payment).stream().map(this::view).toList();
 }
 public List<Long> dueIds(){return payouts.due(clock.instant(),org.springframework.data.domain.PageRequest.of(0,50,org.springframework.data.domain.Sort.by("id")));}
 public List<FinancialAuditEvent> history(Long admin,Long id){readRole(admin);payouts.findById(id).orElseThrow(this::missing);return auditEvents.findByPayoutIdOrderByIdAsc(id);}
 private void audit(InternalPayout p,Long actor,String code,String key){auditEvents.save(FinancialAuditEvent.record(p.getId(),null,null,actor,"payout:"+key,code,p.getState().name()));}
 private boolean profileCurrent(ExpertSettlementProfile profile){
  var info=business.findForUpdate(profile.getBusinessInfoId()).orElseThrow(this::missing);em.refresh(info);
  var file=files.findForOcr(profile.getFileId()).orElseThrow(this::missing);em.refresh(file);
  return info.getExpertProfile().getId().equals(profile.getExpertProfileId()) && info.getInputRevision()==profile.getInputRevision()
   && info.getFile().getId().equals(file.getId()) && file.getOcrRevision()==profile.getOcrRevision()
   && file.getOcrStatus()==com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED && info.isConfirmedFor(file.getId(),file.getOcrRevision());
 }
 private boolean pendingRefund(Long payment){return !em.createNativeQuery("SELECT id FROM payment_refund_requests WHERE payment_id=? AND state NOT IN ('FAILED','REJECTED','SUCCEEDED') FOR UPDATE").setParameter(1,payment).getResultList().isEmpty();}
 private ExpertSettlementProfile latestProfile(Long expert){
  var owner=em.find(com.capdi.backend.domain.expert.entity.ExpertProfile.class,expert);if(owner==null)throw missing();
  if(!"EXPERT".equals(sources.userType(owner.getUser().getId(),true).orElse("")))throw forbidden();
  var rows=em.createNativeQuery("SELECT * FROM expert_settlement_profiles WHERE expert_profile_id=? ORDER BY revision DESC LIMIT 1 FOR UPDATE",ExpertSettlementProfile.class).setParameter(1,expert).getResultList();
  if(rows.isEmpty())throw missing();return (ExpertSettlementProfile)rows.getFirst();
 }
 private void readRole(Long id){if(id==null || !"ADMIN".equals(sources.userType(id,false).orElse("")))throw forbidden();}
 private View view(InternalPayout p){return new View(p.getId(),p.getPaymentId(),p.getReviewId(),p.getAmount(),p.getState(),p.isDispatched(),p.getVersion(),p.getState()==InternalPayout.State.SUCCEEDED && "PRODUCTION".equals(p.getProviderEnvironment()));}
 private SettlementBalance balance(Long id){var b=balances.findByPaymentId(id).orElseThrow(this::missing);em.refresh(b);return b;}
 private Payment lock(Long id){var p=payments.lockById(id).orElseThrow(this::missing);em.refresh(p);return p;}
 private void role(Long id){if(id==null || !"ADMIN".equals(sources.userType(id,true).orElse("")))throw forbidden();}
 private void enabled(){if(!policy.reviewed(FinancialGateway.Kind.PAYOUT) || !provider.enabled() || provider.identity()==null || provider.identity().code()==null || !provider.identity().code().matches("[A-Za-z0-9_-]{1,50}") || provider.identity().environment()==null || !java.util.Set.of("TEST","SANDBOX","PRODUCTION").contains(provider.identity().environment()))throw new CustomException(ErrorCode.PAYMENT_FINANCIAL_POLICY_UNCONFIRMED);}
 private void post(InternalPayout p,String action,Map<FinancialLedgerEntry.Bucket,BigDecimal> lines){
  if(lines.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).signum()!=0)throw conflict();
  ledger.saveAllAndFlush(lines.entrySet().stream().map(e->FinancialLedgerEntry.line(p.getPaymentId(),null,"internal-payout:"+p.getId()+":"+action,e.getKey(),e.getValue())).toList());
 }
 private CustomException missing(){return new CustomException(ErrorCode.NOT_FOUND);}
 private CustomException forbidden(){return new CustomException(ErrorCode.FORBIDDEN);}
 private CustomException conflict(){return new CustomException(ErrorCode.PAYMENT_EXECUTION_CONFLICT);}
}
