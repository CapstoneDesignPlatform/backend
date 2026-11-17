package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.domain.payment.gateway.*;
import com.capdi.backend.domain.payment.config.*;
import com.capdi.backend.domain.payment.util.PaymentProcessingKeys;
import com.capdi.backend.global.exception.*;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class InternalTaxExecutionPersistenceService {
 private final PaymentSourceRepository sources;private final PaymentRepository payments;private final SettlementBalanceRepository balances;
 private final InternalTaxItemRepository items;
 private final InternalTaxDocumentRepository documents;private final InternalTaxExecutionRepository executions;
 private final ExpertSettlementProfileRepository profiles;private final FinancialSnapshotGuard guard;
 private final FinancialTaxInvoiceRepository invoices;private final PaymentSettlementDraftRepository settlementDrafts;
 private final PaymentTaxInvoiceDraftRepository drafts;private final BillingPartySnapshotRepository parties;
 private final FinancialAuditEventRepository events;private final FinancialPolicy policy;private final TaxExecutionAuthorization authorization;
 private final TaxInvoiceProvider provider;private final FinancialVault vault;private final ObjectMapper mapper;private final EntityManager em;private final Clock clock;
 public record View(Long id,Long document_id,Long invoice_id,InternalTaxExecution.State state,TaxInvoiceProvider.TransmissionState nts_state,
  boolean test_issuance,boolean legally_issued,boolean snapshot_current,int lookup_count,String failure_code,long version){}
 public record Run(Long id,String token,TaxInvoiceProvider.Request request,String externalId){}
 public boolean current(InternalTaxDocument d,boolean lock){
  if(d.isSuperseded() || d.getBalanceVersion()==null || d.getProfileId()==null)return false;
  var b=balances.findByPaymentId(d.getPaymentId()).orElse(null);if(b==null)return false;if(lock)em.refresh(b);
  var p=profiles.findById(d.getProfileId()).orElse(null);
  if(p==null || b.getVersion()!=d.getBalanceVersion() || !guard.profileApproved(p,lock) || !Objects.equals(d.getProfileReviewId(),guard.latestReviewId(p,lock)))return false;
  if(!em.createNativeQuery("SELECT id FROM payment_refund_requests WHERE payment_id=? AND state NOT IN ('FAILED','REJECTED','SUCCEEDED')"+(lock?" FOR UPDATE":"")).setParameter(1,d.getPaymentId()).getResultList().isEmpty())return false;
  BigDecimal expected=d.getKind()==PaymentTaxInvoiceDraft.Kind.EXPERT_TO_CLIENT?b.net():b.platform();
  return d.getOriginalDocumentId()!=null || d.getTotal().compareTo(expected)==0;
 }
 public boolean policyAllows(InternalTaxDocument d,boolean lock){
  if(d.getState()==InternalTaxDocument.State.APPROVED && !Objects.equals(d.getReviewedPolicyVersion(),policy.taxPolicyVersion()))return false;
  if(!authorization.approved() || !policy.reviewed(FinancialGateway.Kind.TAX_INVOICE) || "UNREVIEWED".equals(policy.taxPolicyVersion())
   || !provider.enabled() || !vault.available() || !current(d,lock))return false;
  var identity=provider.identity();
  if(identity==null || identity.code()==null || !identity.code().matches("[A-Za-z0-9_-]{1,50}") || identity.environment()==null || !Set.of("TEST","SANDBOX","PRODUCTION").contains(identity.environment()))return false;
  return policy.authorizedSupplierNumbers().contains(party(d.getSupplierSnapshotId()).path("brn").asText());
 }
 @Transactional public View prepare(Long admin,Long document,String rawKey,long expectedVersion){
  role(admin);var d=lock(document);String key=PaymentProcessingKeys.requestKey(rawKey);
  var old=executions.findByDocumentId(document);
  if(old.isPresent()){if(!old.get().getRequestKey().equals(key) || old.get().getDocumentVersion()!=expectedVersion)throw conflict();return view(old.get());}
  if(d.getVersion()!=expectedVersion || d.getState()!=InternalTaxDocument.State.APPROVED || !current(d,true))throw conflict();
  if(!policyAllows(d,true))throw unavailable();
  var p=payments.findById(d.getPaymentId()).orElseThrow(this::missing);if(p.getStatus()!=PaymentStatus.PAID)throw conflict();
  FinancialTaxInvoice original=null;
  if(d.getOriginalDocumentId()!=null){
   var source=executions.findByDocumentId(d.getOriginalDocumentId()).orElseThrow(this::missing);
   original=invoices.findById(source.getInvoiceId()).orElseThrow(this::missing);em.refresh(original);
   if(source.getState()!=InternalTaxExecution.State.ISSUED || source.getNtsState()!=TaxInvoiceProvider.TransmissionState.SUCCEEDED || !original.isIssued())throw conflict();
   if(d.getModification()==FinancialTaxInvoice.Modification.CANCELLATION_OF_CONTRACT &&
    (d.getSupply().compareTo(original.getSupply().negate())!=0 || d.getVat().compareTo(original.getVat().negate())!=0))throw conflict();
  }
  String scope=original==null?"BASE:"+p.getId()+":"+d.getKind():"CORRECTION:"+original.getId()+":"+d.getModification();
  if(original!=null && d.getModification()!=FinancialTaxInvoice.Modification.CANCELLATION_OF_CONTRACT && d.getModification()!=FinancialTaxInvoice.Modification.DUPLICATE_ISSUANCE_DUE_TO_ERROR){
   var request=new com.capdi.backend.domain.payment.dto.FinancialWorkflowRequests.Invoice(d.getSupplierSnapshotId(),d.getRecipientSnapshotId(),d.getSupply(),d.getVat(),d.getTaxation(),d.getWriteDate(),original.getId(),d.getModification());
   try{scope+=":"+PaymentProcessingKeys.digest(mapper.writeValueAsString(request));}catch(Exception ex){throw conflict();}
  }
  String scopeKey=PaymentProcessingKeys.digest(scope);
  if(invoices.findByIssuanceScopeKey(scopeKey).isPresent())throw conflict();
  var draft=settlementDrafts.findByPaymentId(p.getId()).orElseGet(()->settlementDrafts.saveAndFlush(PaymentSettlementDraft.create(p)));
  var taxDraft=drafts.saveAndFlush(PaymentTaxInvoiceDraft.create(draft.getId(),PaymentProcessingKeys.digest("internal-document:"+d.getId()),d.getKind(),original==null?null:original.getDraftId()));
  var invoice=FinancialTaxInvoice.create(taxDraft.getId(),p.getId(),d.getSupplierSnapshotId(),d.getRecipientSnapshotId(),original==null?null:original.getId(),d.getModification(),d.getTaxation(),d.getWriteDate(),d.getSupply(),d.getVat(),scopeKey);
  invoice.claimExecutionOwner("INTERNAL");invoice=invoices.saveAndFlush(invoice);
  String hash=PaymentProcessingKeys.digest(d.getRequestHash()+":"+d.getBalanceVersion()+":"+d.getProfileId()+":"+policy.taxPolicyVersion());
  var e=InternalTaxExecution.ready(d.getId(),invoice.getId(),key,hash,policy.taxPolicyVersion(),provider.identity().code(),provider.identity().environment());
  e.captureDocumentVersion(d.getVersion());e=executions.saveAndFlush(e);audit(e,admin,"PREPARED","prepare:"+e.getId());return view(e);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW) public Run claimAdmin(Long admin,Long id,boolean lookup){
  role(admin);return claim(id,lookup,admin,false);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW) public Run claimWorker(Long id){return claim(id,true,null,true);}
 private Run claim(Long id,boolean lookup,Long actor,boolean worker){
  var e=executions.findById(id).orElseThrow(this::missing);var d=lock(e.getDocumentId());em.refresh(e);
  if(!provider.enabled() || !e.getProviderCode().equals(provider.identity().code()) || !e.getProviderEnvironment().equals(provider.identity().environment()))throw unavailable();
  if(worker && !due(e))throw conflict();
  if(!lookup && (d.getState()!=InternalTaxDocument.State.APPROVED || d.getVersion()!=e.getDocumentVersion() || !current(d,true) || !policyAllows(d,true) || !e.getPolicyVersion().equals(policy.taxPolicyVersion())))throw conflict();
  if(lookup && !worker && e.getLookupCount()>=8){e.allowManualLookup(clock.instant());audit(e,actor,"MANUAL_LOOKUP_RESET","reset:"+e.getId()+":"+e.getVersion());}
  String token;try{token=e.claim(lookup,clock.instant());}catch(IllegalStateException ex){throw conflict();}
  var invoice=invoices.findById(e.getInvoiceId()).orElseThrow(this::missing);em.refresh(invoice);
  if(!"INTERNAL".equals(invoice.getExecutionOwner()))throw conflict();
  String original=null;if(invoice.getOriginalInvoiceId()!=null)original=invoices.findById(invoice.getOriginalInvoiceId()).orElseThrow(this::missing).getExternalId();
  var request=new TaxInvoiceProvider.Request(e.getIdentity(),"financial-invoice:"+invoice.getId(),original,e.getSnapshotHash(),invoice.getSupply(),invoice.getVat(),invoice.getTotal(),"KRW",invoice.getWriteDate(),invoice.getTaxation().name(),party(invoice.getSupplierSnapshotId()).toString(),party(invoice.getRecipientSnapshotId()).toString(),invoice.getModification()==null?null:invoice.getModification().name(),items.findByDocumentIdOrderByLineNumber(d.getId()).stream().map(i->new TaxInvoiceProvider.Item(i.getItemCode(),i.getSupply(),i.getVat())).toList());
  executions.flush();audit(e,actor,lookup?"LOOKUP_CLAIM":"ISSUE_CLAIM","claim:"+token);
  return new Run(id,token,request,e.getExternalId());
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW) public View complete(Run run,TaxInvoiceProvider.Result result){
  var e=executions.findById(run.id()).orElseThrow(this::missing);lock(e.getDocumentId());em.refresh(e);
  if(!e.owns(run.token()))throw conflict();
  var invoice=invoices.findById(e.getInvoiceId()).orElseThrow(this::missing);em.refresh(invoice);
  boolean valid=result!=null && result.authoritative() && result.issuance()!=null && result.transmission()!=null
   && e.getIdentity().equals(result.documentIdentity()) && e.getSnapshotHash().equals(result.snapshotHash())
   && "KRW".equals(result.currency()) && result.supply()!=null && result.vat()!=null && result.total()!=null
   && invoice.getSupply().compareTo(result.supply())==0 && invoice.getVat().compareTo(result.vat())==0 && invoice.getTotal().compareTo(result.total())==0
   && result.testEnvironment()==!"PRODUCTION".equals(e.getProviderEnvironment())
   && (result.providerDocumentId()==null || result.providerDocumentId().matches("[A-Za-z0-9_.:-]{1,200}"))
   && (result.issuance()!=TaxInvoiceProvider.IssuanceState.ISSUED || result.providerDocumentId()!=null)
   && (e.getExternalId()==null || e.getExternalId().equals(result.providerDocumentId()));
  var state=valid?switch(result.issuance()){case ISSUED->InternalTaxExecution.State.ISSUED;case FAILED->InternalTaxExecution.State.FAILED;case REQUESTED->InternalTaxExecution.State.REQUESTED;default->InternalTaxExecution.State.UNKNOWN;}:InternalTaxExecution.State.UNKNOWN;
  var nts=valid?result.transmission():e.getNtsState();
  e.observe(run.token(),state,nts,valid?result.providerDocumentId():null,valid && result.testEnvironment(),valid?null:"UNCONFIRMED_EVIDENCE",clock.instant());
  if(valid){
   var proof=new FinancialGateway.Evidence(e.getIdentity(),result.providerDocumentId(),state==InternalTaxExecution.State.ISSUED?FinancialGateway.State.SUCCEEDED:state==InternalTaxExecution.State.FAILED?FinancialGateway.State.FAILED:FinancialGateway.State.PENDING,invoice.getTotal(),"KRW",false,false,FinancialGateway.NtsState.valueOf(nts.name()));
   invoice.bindEnvironment(e.getProviderEnvironment());invoice.observe(proof);
  }
  executions.flush();invoices.flush();audit(e,null,valid?"PROVIDER_OBSERVED":"UNCONFIRMED","result:"+run.token());return view(e);
 }
 public View read(Long admin,Long id){readRole(admin);return view(executions.findById(id).orElseThrow(this::missing));}
 public List<Long> dueIds(){return executions.due(clock.instant(),org.springframework.data.domain.PageRequest.of(0,50,org.springframework.data.domain.Sort.by("id")));}
 private boolean due(InternalTaxExecution e){var now=clock.instant();return e.isDispatched() && e.getLookupCount()<8 && e.getState()!=InternalTaxExecution.State.FAILED
  && !(e.getState()==InternalTaxExecution.State.ISSUED && e.getNtsState()==TaxInvoiceProvider.TransmissionState.SUCCEEDED)
  && (e.getLeaseUntil()==null || !e.getLeaseUntil().isAfter(now)) && (e.getNextLookupAt()==null || !e.getNextLookupAt().isAfter(now));}
 public List<FinancialAuditEvent> history(Long admin,Long id){readRole(admin);executions.findById(id).orElseThrow(this::missing);return events.findByTaxExecutionIdOrderByIdAsc(id);}
 private View view(InternalTaxExecution e){var d=documents.findById(e.getDocumentId()).orElseThrow(this::missing);return new View(e.getId(),d.getId(),e.getInvoiceId(),e.getState(),e.getNtsState(),e.isTestIssuance(),e.getState()==InternalTaxExecution.State.ISSUED && !e.isTestIssuance() && "PRODUCTION".equals(e.getProviderEnvironment()),current(d,false),e.getLookupCount(),e.getFailureCode(),e.getVersion());}
 private InternalTaxDocument lock(Long id){var d=documents.findById(id).orElseThrow(this::missing);var p=payments.lockById(d.getPaymentId()).orElseThrow(this::missing);em.refresh(p);em.refresh(d);return d;}
 private JsonNode party(Long id){var p=parties.findById(id).orElseThrow(this::missing);try{return mapper.readTree(vault.open("party:"+p.getRole()+":"+p.getSubjectUserId()+":"+p.getCreatedBy(),p.getEncryptedDetails()));}catch(Exception ex){throw unavailable();}}
 private void audit(InternalTaxExecution e,Long actor,String code,String key){events.save(FinancialAuditEvent.record(null,e.getId(),null,actor,"tax:"+key,code,e.getState().name()));}
 private void role(Long user){if(user==null || !"ADMIN".equals(sources.userType(user,true).orElse("")))throw new CustomException(ErrorCode.FORBIDDEN);}
 private void readRole(Long user){if(user==null || !"ADMIN".equals(sources.userType(user,false).orElse("")))throw new CustomException(ErrorCode.FORBIDDEN);}
 private CustomException missing(){return new CustomException(ErrorCode.NOT_FOUND);}
 private CustomException conflict(){return new CustomException(ErrorCode.PAYMENT_EXECUTION_CONFLICT);}
 private CustomException unavailable(){return new CustomException(ErrorCode.PAYMENT_FINANCIAL_POLICY_UNCONFIRMED);}
}
