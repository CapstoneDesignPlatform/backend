package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.dto.InternalTaxRequests;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.domain.payment.config.FinancialPolicy;
import com.capdi.backend.domain.payment.gateway.FinancialGateway;
import com.capdi.backend.domain.payment.util.PaymentProcessingKeys;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.global.exception.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class InternalTaxService {
 private final PaymentSourceRepository sources;private final PaymentRepository payments;
 private final InternalTaxDocumentRepository documents;private final InternalTaxItemRepository items;
 private final FinancialSnapshotGuard snapshotGuard;
 private final InternalTaxExecutionPersistenceService execution;
 private final InternalTaxExecutionRepository executions;private final ExpertSettlementProfileRepository profiles;private final SettlementBalanceRepository balances;
 private final FinancialAuditEventRepository events;
 private final BillingPartySnapshotRepository parties;private final FinancialPolicy policy;private final EntityManager em;
 public record ItemView(int line_number,String item_code,BigDecimal supply,BigDecimal vat){}
 public record View(Long id,Long payment_id,Long original_document_id,PaymentTaxInvoiceDraft.Kind kind,
  InternalTaxDocument.State state,BigDecimal supply,BigDecimal vat,BigDecimal total,long version,
  boolean legally_issued,boolean issuance_executable,FinancialGateway.NtsState nts_state,List<ItemView> items,boolean snapshot_current,Long revision_of_id,boolean superseded){}
 @Transactional public View create(Long admin,Long payment,String rawKey,InternalTaxRequests.Draft body){return createDraft(admin,payment,rawKey,body,null);}
 private View createDraft(Long admin,Long payment,String rawKey,InternalTaxRequests.Draft body,Long revisionOf){
  role(admin);var p=payments.lockById(payment).orElseThrow(this::missing);em.refresh(p);if(p.getStatus()!=PaymentStatus.PAID)throw conflict();
  String key=PaymentProcessingKeys.requestKey(rawKey);
  String scope=revisionOf!=null?PaymentProcessingKeys.digest("REVISION:"+revisionOf+":"+key):PaymentProcessingKeys.digest(body.originalDocumentId()==null?"BASE:"+payment+":"+body.kind():"CORRECTION:"+body.originalDocumentId()+":"+key);
  String hash=PaymentProcessingKeys.digest(body.toString());
  var old=documents.findByScopeKey(scope);
  if(old.isPresent()){if(!old.get().getRequestHash().equals(hash))throw new CustomException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);return view(old.get());}
  var supplier=parties.findById(body.supplierSnapshotId()).orElseThrow(this::missing);
  var recipient=parties.findById(body.recipientSnapshotId()).orElseThrow(this::missing);
  Long expert=em.find(ExpertProfile.class,p.getExpertProfileId()).getUser().getId();
  if(body.kind()==PaymentTaxInvoiceDraft.Kind.EXPERT_TO_CLIENT){
   if(supplier.getRole()!=BillingPartySnapshot.Role.EXPERT || !Objects.equals(supplier.getSubjectUserId(),expert)
    || recipient.getRole()!=BillingPartySnapshot.Role.CLIENT || !Objects.equals(recipient.getSubjectUserId(),p.getClientUserId()))throw conflict();
  }else if(supplier.getRole()!=BillingPartySnapshot.Role.PLATFORM || recipient.getRole()!=BillingPartySnapshot.Role.EXPERT || !Objects.equals(recipient.getSubjectUserId(),expert))throw conflict();
  if(body.originalDocumentId()!=null){
   var original=documents.findById(body.originalDocumentId()).orElseThrow(this::missing);
   if(!original.getPaymentId().equals(payment) || original.getKind()!=body.kind() || !original.getSupplierSnapshotId().equals(supplier.getId())
    || !original.getRecipientSnapshotId().equals(recipient.getId()) || original.getOriginalDocumentId()!=null)throw conflict();
   // This remains a correction draft. It cannot become a legally issued correction until original issuance is verified.
  }
  BigDecimal supply=body.items().stream().map(InternalTaxRequests.Item::supply).reduce(BigDecimal.ZERO,BigDecimal::add);
  BigDecimal vat=body.items().stream().map(InternalTaxRequests.Item::vat).reduce(BigDecimal.ZERO,BigDecimal::add);
  if(supply.compareTo(body.supply())!=0 || vat.compareTo(body.vat())!=0)throw invalid();
  InternalTaxDocument d;
  try{d=InternalTaxDocument.draft(payment,scope,hash,body.originalDocumentId(),supplier.getId(),recipient.getId(),body.kind(),body.taxation(),body.modification(),body.writeDate(),body.supply(),body.vat(),body.total());}
  catch(IllegalArgumentException ex){throw invalid();}
  d.revisionOf(revisionOf);var balance=balances.findByPaymentId(payment);if(balance.isPresent()){em.refresh(balance.get());d.captureBalance(balance.get().getVersion());}
  d.captureProfile(profiles.findFirstByExpertProfileIdOrderByRevisionDesc(p.getExpertProfileId()).map(ExpertSettlementProfile::getId).orElse(null));
  if(d.getProfileId()!=null)d.captureProfileReview(profiles.findById(d.getProfileId()).map(v->snapshotGuard.latestReviewId(v,false)).orElse(null));
  d=documents.saveAndFlush(d);int line=1;
  for(var item:body.items())items.save(InternalTaxItem.line(d.getId(),line++,item.itemCode(),item.supply(),item.vat()));
  items.flush();events.save(FinancialAuditEvent.record(null,null,null,admin,"tax-draft:"+d.getId(),"DRAFT_CREATED",d.getState().name()).document(d.getId()).evidence(null,d.getRequestHash()));return view(d);
 }
 @Transactional public View review(Long admin,Long id,InternalTaxRequests.Review request){
  role(admin);var d=documents.findById(id).orElseThrow(this::missing);payments.lockById(d.getPaymentId()).orElseThrow(this::missing);em.refresh(d);
  if(d.isSuperseded() || executions.findByDocumentId(id).isPresent())throw conflict();
  if(d.getVersion()!=request.expectedVersion()){if(d.getVersion()==request.expectedVersion()+1 && Objects.equals(d.getReviewerId(),admin) && Objects.equals(d.getReasonCode(),request.reasonCode()) && (request.approve()?d.getState()!=InternalTaxDocument.State.REJECTED:d.getState()==InternalTaxDocument.State.REJECTED))return view(d);throw conflict();}
  // Legal party authority, VAT and correction eligibility are not established by a local review.
  try{d.review(admin,request.approve(),request.reasonCode(),execution.policyAllows(d,true));d.recordPolicy(policy.taxPolicyVersion());}catch(IllegalStateException ex){throw conflict();}
  documents.flush();events.save(FinancialAuditEvent.record(null,null,null,admin,"tax-review:"+d.getId()+":"+d.getVersion(),"DOCUMENT_REVIEW",d.getState().name()).document(d.getId()).evidence(request.reasonCode(),d.getRequestHash()));return view(d);
 }


 @Transactional public View revise(Long admin,Long id,String key,long expectedVersion,InternalTaxRequests.Draft body){
  role(admin);var old=documents.findById(id).orElseThrow(this::missing);payments.lockById(old.getPaymentId()).orElseThrow(this::missing);em.refresh(old);
  String scope=PaymentProcessingKeys.digest("REVISION:"+id+":"+PaymentProcessingKeys.requestKey(key));
  var prior=documents.findByScopeKey(scope);if(prior.isPresent()){if(!prior.get().getRequestHash().equals(PaymentProcessingKeys.digest(body.toString())))throw conflict();return view(prior.get());}
  if(old.isSuperseded() || old.getVersion()!=expectedVersion || executions.findByDocumentId(id).isPresent() || body.kind()!=old.getKind()
   || !Objects.equals(body.supplierSnapshotId(),old.getSupplierSnapshotId()) || !Objects.equals(body.recipientSnapshotId(),old.getRecipientSnapshotId()) || !Objects.equals(body.originalDocumentId(),old.getOriginalDocumentId()))throw conflict();
  var result=createDraft(admin,old.getPaymentId(),key,body,id);old.supersede();documents.flush();
  events.save(FinancialAuditEvent.record(null,null,null,admin,"tax-superseded:"+id,"DRAFT_SUPERSEDED",old.getState().name()).document(id));return result;
 }
 @Transactional public View refreshSnapshot(Long admin,Long id,long expectedVersion){
  role(admin);var d=documents.findById(id).orElseThrow(this::missing);var p=payments.lockById(d.getPaymentId()).orElseThrow(this::missing);em.refresh(d);
  if(d.isSuperseded() || executions.findByDocumentId(id).isPresent() || d.getVersion()!=expectedVersion)throw conflict();
  var b=balances.findByPaymentId(p.getId()).orElseThrow(this::missing);em.refresh(b);
  var profile=profiles.findFirstByExpertProfileIdOrderByRevisionDesc(p.getExpertProfileId()).orElseThrow(this::missing);
  d.refreshSource(b.getVersion(),profile.getId());d.captureProfileReview(snapshotGuard.latestReviewId(profile,true));documents.flush();
  events.save(FinancialAuditEvent.record(null,null,null,admin,"tax-refresh:"+d.getId()+":"+d.getVersion(),"SOURCE_REFRESHED",d.getState().name()).document(d.getId()).evidence(null,d.getRequestHash()));return view(d);
 }
 public List<FinancialAuditEvent> history(Long admin,Long id){readRole(admin);documents.findById(id).orElseThrow(this::missing);return events.findByDocumentIdOrderByIdAsc(id);}
 public View read(Long admin,Long id){readRole(admin);return view(documents.findById(id).orElseThrow(this::missing));}
 public List<View> list(Long admin,Long payment){readRole(admin);return documents.findByPaymentIdOrderByIdDesc(payment).stream().map(this::view).toList();}
 private View view(InternalTaxDocument d){var result=executions.findByDocumentId(d.getId());boolean issued=result.map(e->e.getState()==InternalTaxExecution.State.ISSUED && !e.isTestIssuance() && "PRODUCTION".equals(e.getProviderEnvironment())).orElse(false);return new View(d.getId(),d.getPaymentId(),d.getOriginalDocumentId(),d.getKind(),d.getState(),d.getSupply(),d.getVat(),d.getTotal(),d.getVersion(),issued,d.getState()==InternalTaxDocument.State.APPROVED && result.map(e->!e.isDispatched()).orElse(true) && execution.policyAllows(d,false),result.map(e->FinancialGateway.NtsState.valueOf(e.getNtsState().name())).orElse(FinancialGateway.NtsState.NOT_SENT),
  items.findByDocumentIdOrderByLineNumber(d.getId()).stream().map(i->new ItemView(i.getLineNumber(),i.getItemCode(),i.getSupply(),i.getVat())).toList(),execution.current(d,false),d.getRevisionOfId(),d.isSuperseded());}
 private void readRole(Long user){if(user==null || !"ADMIN".equals(sources.userType(user,false).orElse("")))throw new CustomException(ErrorCode.FORBIDDEN);}
 private void role(Long user){if(user==null || !"ADMIN".equals(sources.userType(user,true).orElse("")))throw new CustomException(ErrorCode.FORBIDDEN);}
 private CustomException missing(){return new CustomException(ErrorCode.NOT_FOUND);}
 private CustomException invalid(){return new CustomException(ErrorCode.INVALID_INPUT);}
 private CustomException conflict(){return new CustomException(ErrorCode.PAYMENT_EXECUTION_CONFLICT);}
}
