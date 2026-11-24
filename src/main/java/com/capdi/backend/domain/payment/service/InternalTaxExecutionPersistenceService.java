package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.dto.FinancialWorkflowResponse;
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
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class InternalTaxExecutionPersistenceService {
 private final PaymentSourceRepository sources;private final PaymentRepository payments;private final SettlementBalanceRepository balances;
 private final InternalTaxItemRepository items;
 private final InternalTaxDocumentRepository documents;private final InternalTaxExecutionRepository executions;
 private final ExpertSettlementProfileRepository profiles;private final FinancialSnapshotGuard guard;
 private final FinancialTaxInvoiceRepository invoices;private final PaymentSettlementDraftRepository settlementDrafts;
 private final PaymentTaxInvoiceDraftRepository drafts;private final BillingPartySnapshotRepository parties;
 private final FinancialAuditEventRepository events;
 private final FinancialOperationRepository operations;private final FinancialPolicy policy;private final TaxExecutionAuthorization authorization;
 private final SettlementCompletionGuard completionGuard;
 private final FinancialGateway gateway;private final FinancialWorkflowPersistenceService canonical;
 private final FinancialVault vault;private final ObjectMapper mapper;private final EntityManager em;
 public record View(Long id,Long document_id,Long invoice_id,InternalTaxExecution.State state,TaxInvoiceProvider.TransmissionState nts_state,
  boolean test_issuance,boolean legally_issued,boolean snapshot_current,int lookup_count,String failure_code,long version,
  Long financial_operation_id,String canonical_state){}
 public boolean current(InternalTaxDocument d,boolean lock){
  if(d.isSuperseded() || d.getBalanceVersion()==null || d.getProfileId()==null)return false;
  var b=balances.findByPaymentId(d.getPaymentId()).orElse(null);if(b==null)return false;if(lock)em.refresh(b);
  var p=profiles.findById(d.getProfileId()).orElse(null);
  if(p==null || b.getVersion()!=d.getBalanceVersion() || !guard.profileApproved(p,lock) || !Objects.equals(d.getProfileReviewId(),guard.latestReviewId(p,lock)))return false;
  if(!em.createNativeQuery("SELECT id FROM payment_refund_requests WHERE payment_id=? AND state NOT IN ('FAILED','REJECTED','SUCCEEDED')"+(lock?" FOR UPDATE":"")).setParameter(1,d.getPaymentId()).getResultList().isEmpty())return false;
  BigDecimal expected=d.getKind()==PaymentTaxInvoiceDraft.Kind.EXPERT_TO_CLIENT?b.net():b.platform();
  return d.getOriginalDocumentId()!=null || d.getTotal().compareTo(expected)==0;
 }
 public boolean documentReviewAllows(InternalTaxDocument d,boolean lock){
  if(d.getState()==InternalTaxDocument.State.APPROVED && !Objects.equals(d.getReviewedPolicyVersion(),policy.taxPolicyVersion()))return false;
  if(!authorization.approved() || !policy.reviewed(FinancialGateway.Kind.TAX_INVOICE) || "UNREVIEWED".equals(policy.taxPolicyVersion())
   || !vault.available() || !current(d,lock))return false;
  return policy.authorizedSupplierNumbers().contains(party(d.getSupplierSnapshotId()).path("brn").asText());
 }
 public boolean policyAllows(InternalTaxDocument d,boolean lock){
  var identity=gateway.identity();
  return documentReviewAllows(d,lock) && gateway.canSubmit(FinancialGateway.Kind.TAX_INVOICE)
   && identity!=null && identity.code()!=null && identity.code().matches("[A-Za-z0-9_-]{1,50}")
   && identity.environment()!=null && Set.of("TEST","SANDBOX","PRODUCTION").contains(identity.environment());
 }
 @Transactional public View prepare(Long admin,Long document,String rawKey,long expectedVersion){
  role(admin);var d=lock(document);String key=PaymentProcessingKeys.requestKey(rawKey);
  var old=executions.findByDocumentId(document);
  if(old.isPresent()){
   var e=old.get();if(!e.getRequestKey().equals(key) || e.getDocumentVersion()!=expectedVersion)throw conflict();
   if(e.getFinancialOperationId()==null && policyAllows(d,true))linkOperation(admin,d,e,key);
   return view(e);
  }
  if(d.getVersion()!=expectedVersion || d.getState()!=InternalTaxDocument.State.APPROVED || !current(d,true))throw conflict();
  if(!documentReviewAllows(d,true))throw unavailable();
  var p=payments.findById(d.getPaymentId()).orElseThrow(this::missing);if(p.getStatus()!=PaymentStatus.PAID)throw conflict();completionGuard.requireCompleted(p.getAnnouncementId());
  FinancialTaxInvoice original=null;
  if(d.getOriginalDocumentId()!=null){
   var source=executions.findByDocumentId(d.getOriginalDocumentId()).orElseThrow(this::missing);
   original=invoices.findById(source.getInvoiceId()).orElseThrow(this::missing);em.refresh(original);
   var originalOperation=source.getFinancialOperationId()==null?Optional.<FinancialOperation>empty():operations.findById(source.getFinancialOperationId());
   if(originalOperation.isEmpty() || originalOperation.get().getState()!=FinancialOperation.State.SUCCEEDED
     || originalOperation.get().getNtsState()!=FinancialGateway.NtsState.SUCCEEDED || !original.isIssued())throw conflict();
   if(d.getModification()==FinancialTaxInvoice.Modification.CANCELLATION_OF_CONTRACT &&
    (d.getSupply().compareTo(original.getSupply().negate())!=0 || d.getVat().compareTo(original.getVat().negate())!=0))throw conflict();
  }
  String scope=original==null?"BASE:"+p.getId()+":"+d.getKind():"CORRECTION:"+original.getId()+":"+d.getModification();
  if(original!=null && d.getModification()!=FinancialTaxInvoice.Modification.CANCELLATION_OF_CONTRACT && d.getModification()!=FinancialTaxInvoice.Modification.DUPLICATE_ISSUANCE_DUE_TO_ERROR){
   var request=new com.capdi.backend.domain.payment.dto.FinancialWorkflowRequests.Invoice(d.getSupplierSnapshotId(),d.getRecipientSnapshotId(),d.getSupply(),d.getVat(),d.getTaxation(),d.getWriteDate(),original.getId(),d.getModification());
   try{scope+=":"+PaymentProcessingKeys.digest(mapper.writeValueAsString(request));}catch(Exception ex){throw conflict();}
  }
  String scopeKey=PaymentProcessingKeys.digest(scope);
  var existing=invoices.findByIssuanceScopeKey(scopeKey);
  if(existing.isPresent()){
   var found=existing.get();
   if(found.getPaymentId().longValue()!=p.getId() || !Objects.equals(found.getSupplierSnapshotId(),d.getSupplierSnapshotId())
    || !Objects.equals(found.getRecipientSnapshotId(),d.getRecipientSnapshotId()) || found.getTaxation()!=d.getTaxation()
    || !found.getWriteDate().equals(d.getWriteDate()) || found.getSupply().compareTo(d.getSupply())!=0
    || found.getVat().compareTo(d.getVat())!=0 || !Objects.equals(found.getOriginalInvoiceId(),original==null?null:original.getId())
     || found.getInternalDocumentId()!=null && !found.getInternalDocumentId().equals(d.getId()))throw conflict();
   found.linkInternalDocument(d.getId());d.linkFinancialInvoice(found.getId());
   var operation=operations.findByInvoiceId(found.getId());
   var e=makeAlias(d,found,key,operation.map(FinancialOperation::getId).orElse(null),expectedVersion);
   e=executions.saveAndFlush(e);documents.flush();if(e.getFinancialOperationId()==null && policyAllows(d,true))linkOperation(admin,d,e,key);
   audit(e,admin,"CANONICAL_DOCUMENT_LINKED","prepare:"+e.getId());return view(e);
  }
  var draft=settlementDrafts.findByPaymentId(p.getId()).orElseGet(()->settlementDrafts.saveAndFlush(PaymentSettlementDraft.create(p)));
  var taxDraft=drafts.saveAndFlush(PaymentTaxInvoiceDraft.create(draft.getId(),PaymentProcessingKeys.digest("internal-document:"+d.getId()),d.getKind(),original==null?null:original.getDraftId()));
  var invoice=FinancialTaxInvoice.create(taxDraft.getId(),p.getId(),d.getSupplierSnapshotId(),d.getRecipientSnapshotId(),original==null?null:original.getId(),d.getModification(),d.getTaxation(),d.getWriteDate(),d.getSupply(),d.getVat(),scopeKey);
  invoice.claimExecutionOwner("FINANCIAL_OPERATION");invoice.linkInternalDocument(d.getId());invoice=invoices.saveAndFlush(invoice);d.linkFinancialInvoice(invoice.getId());documents.flush();
  String hash=PaymentProcessingKeys.digest(d.getRequestHash()+":"+d.getBalanceVersion()+":"+d.getProfileId()+":"+policy.taxPolicyVersion());
  var e=InternalTaxExecution.ready(d.getId(),invoice.getId(),key,hash,policy.taxPolicyVersion(),gateway.identity().code(),gateway.identity().environment());
  e.captureDocumentVersion(expectedVersion);e=executions.saveAndFlush(e);
  if(policyAllows(d,true))linkOperation(admin,d,e,key);
  executions.flush();audit(e,admin,e.getFinancialOperationId()==null?"PENDING_EXTERNAL_PROVIDER":"CANONICAL_OPERATION_CREATED","prepare:"+e.getId());return view(e);
 }

 private InternalTaxExecution makeAlias(InternalTaxDocument d,FinancialTaxInvoice invoice,String key,Long operationId,long expectedVersion){
  String hash=PaymentProcessingKeys.digest(d.getRequestHash()+":"+d.getBalanceVersion()+":"+d.getProfileId()+":"+policy.taxPolicyVersion());
  var e=InternalTaxExecution.ready(d.getId(),invoice.getId(),key,hash,policy.taxPolicyVersion(),gateway.identity().code(),gateway.identity().environment());
  e.captureDocumentVersion(expectedVersion);if(operationId!=null)e.linkFinancialOperation(operationId);return e;
 }
 private void linkOperation(Long admin,InternalTaxDocument d,InternalTaxExecution e,String key){
  var operation=operations.findByInvoiceId(e.getInvoiceId());
  FinancialWorkflowResponse response;
  if(operation.isPresent()){
   if(operation.get().getKind()!=FinancialGateway.Kind.TAX_INVOICE || !Objects.equals(operation.get().getPaymentId(),d.getPaymentId())
     || !Objects.equals(operation.get().getInvoiceId(),e.getInvoiceId()))throw conflict();
   response=FinancialWorkflowResponse.operation(operation.get());
  }else response=canonical.prepareInvoiceOperation(admin,e.getInvoiceId(),key);
  if(!"TAX_INVOICE".equals(response.getType()) || response.getId()==null)throw conflict();
  e.linkFinancialOperation(response.getId());executions.flush();
 }
 @Transactional public Long canonicalOperationId(Long admin,Long id){
  readRole(admin);var e=executions.findById(id).orElseThrow(this::missing);
  if(e.getFinancialOperationId()==null){
   var operation=operations.findByInvoiceId(e.getInvoiceId());
   if(operation.isPresent()){
    var candidate=operation.get();
    if(candidate.getKind()!=FinancialGateway.Kind.TAX_INVOICE || !Objects.equals(candidate.getPaymentId(),documents.findById(e.getDocumentId()).orElseThrow(this::missing).getPaymentId())
       || !Objects.equals(candidate.getInvoiceId(),e.getInvoiceId()))throw conflict();
    e.linkFinancialOperation(candidate.getId());executions.flush();
   }
  }
  return e.getFinancialOperationId();
 }
 public int dueIdsCount(){return 0;} // FinancialOperation is the only recovery queue owner.
 public View read(Long admin,Long id){readRole(admin);return view(executions.findById(id).orElseThrow(this::missing));}
 public View compatibilityView(InternalTaxExecution execution){return view(execution);}
 public List<FinancialAuditEvent> history(Long admin,Long id){readRole(admin);executions.findById(id).orElseThrow(this::missing);return events.findByTaxExecutionIdOrderByIdAsc(id);}
 private View view(InternalTaxExecution e){
  var d=documents.findById(e.getDocumentId()).orElseThrow(this::missing);
  var operation=e.getFinancialOperationId()==null?Optional.<FinancialOperation>empty():operations.findById(e.getFinancialOperationId());
  var state=operation.map(o->switch(o.getState()){
   case READY->InternalTaxExecution.State.READY;case REQUESTED,PENDING->InternalTaxExecution.State.REQUESTED;
   case SUCCEEDED->InternalTaxExecution.State.ISSUED;case FAILED->InternalTaxExecution.State.FAILED;
   case UNKNOWN,BLOCKED->InternalTaxExecution.State.UNKNOWN;
  }).orElse(e.getState());
  var nts=operation.map(o->TaxInvoiceProvider.TransmissionState.valueOf(o.getNtsState().name())).orElse(e.getNtsState());
  var invoice=invoices.findById(e.getInvoiceId()).orElseThrow(this::missing);
  boolean test=operation.map(o->!"PRODUCTION".equals(o.getProviderEnvironment())).orElse(e.isTestIssuance());
  boolean legal=invoice.isIssued() && !test && "PRODUCTION".equals(invoice.getProviderEnvironment())
      && nts==TaxInvoiceProvider.TransmissionState.SUCCEEDED;
  String failure=operation.map(o->"NONE".equals(o.getFailureCode())?null:o.getFailureCode()).orElse(e.getFailureCode());
  int lookup=operation.map(FinancialOperation::getLookupCount).orElse(e.getLookupCount());
  String canonicalState=operation.map(o->o.getState().name()).orElse(e.isDispatched()?"LEGACY_EXECUTION_UNLINKED":"PENDING_EXTERNAL_PROVIDER");
  return new View(e.getId(),d.getId(),e.getInvoiceId(),state,nts,test,legal,current(d,false),lookup,failure,e.getVersion(),e.getFinancialOperationId(),canonicalState);
 }
 private InternalTaxDocument lock(Long id){var d=documents.findById(id).orElseThrow(this::missing);var p=payments.lockById(d.getPaymentId()).orElseThrow(this::missing);em.refresh(p);em.refresh(d);return d;}
 private JsonNode party(Long id){var p=parties.findById(id).orElseThrow(this::missing);try{return mapper.readTree(vault.open("party:"+p.getRole()+":"+p.getSubjectUserId()+":"+p.getCreatedBy(),p.getEncryptedDetails()));}catch(Exception ex){throw unavailable();}}
 private void audit(InternalTaxExecution e,Long actor,String code,String key){events.save(FinancialAuditEvent.record(null,e.getId(),null,actor,"tax:"+key,code,e.getState().name()));}
 private void role(Long user){if(user==null || !"ADMIN".equals(sources.userType(user,true).orElse("")))throw new CustomException(ErrorCode.FORBIDDEN);}
 private void readRole(Long user){if(user==null || !"ADMIN".equals(sources.userType(user,false).orElse("")))throw new CustomException(ErrorCode.FORBIDDEN);}
 private CustomException missing(){return new CustomException(ErrorCode.NOT_FOUND);}
 private CustomException conflict(){return new CustomException(ErrorCode.PAYMENT_EXECUTION_CONFLICT);}
 private CustomException unavailable(){return new CustomException(ErrorCode.PAYMENT_FINANCIAL_POLICY_UNCONFIRMED);}
}
