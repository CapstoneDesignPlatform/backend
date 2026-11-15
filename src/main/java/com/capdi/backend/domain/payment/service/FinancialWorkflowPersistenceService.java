package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.payment.config.FinancialPolicy;
import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.FinancialGateway;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.domain.payment.util.PaymentProcessingKeys;
import com.capdi.backend.domain.expert.entity.*;
import com.capdi.backend.domain.expert.repository.*;
import com.capdi.backend.domain.expert.util.BusinessInputValidator;
import com.capdi.backend.global.exception.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import static com.capdi.backend.domain.payment.entity.FinancialLedgerEntry.Bucket.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class FinancialWorkflowPersistenceService {
 private final PaymentSourceRepository sources;private final PaymentRepository payments;
 private final PaymentRefundRequestRepository refunds;private final ExpertSellerDraftRepository sellers;
 private final PaymentTaxInvoiceDraftRepository drafts;private final PaymentSettlementDraftRepository settlementDrafts;
 private final FinancialOperationRepository operations;private final ExpertSettlementAccountRepository accounts;
 private final SettlementBalanceRepository balances;private final FinancialApprovalRepository approvals;
 private final FinancialLedgerEntryRepository ledger;private final SettlementAdjustmentRepository adjustments;
 private final BillingPartySnapshotRepository parties;private final FinancialTaxInvoiceRepository invoices;
 private final FinancialOperationAuditRepository audits;private final BusinessRegistrationInfoRepository business;
 private final ExpertFileRepository files;private final BusinessInputValidator validator;
 private final FinancialVault vault;private final FinancialPolicy policy;private final FinancialGateway gateway;
 private final ObjectMapper mapper;private final EntityManager em;private final Clock clock;
 public record Run(Long operation,String token,FinancialGateway.Command command,String externalId,boolean execute) {}

 @Transactional
 public FinancialWorkflowResponse saveAccount(Long user,FinancialWorkflowRequests.Account request) {
  role(user,"EXPERT",true);sensitive();Long expert=profile(user);
  var old=accounts.findFirstByExpertProfileIdOrderByAccountRevisionDesc(expert);
  if(old.isPresent() && !operations.findByAccountIdAndKindAndStateIn(old.get().getId(),FinancialGateway.Kind.PAYOUT,
    List.of(FinancialOperation.State.REQUESTED,FinancialOperation.State.PENDING,FinancialOperation.State.UNKNOWN,FinancialOperation.State.BLOCKED)).stream().noneMatch(FinancialOperation::isSubmitClaimed))throw conflict();
  long revision=old.map(a->a.getAccountRevision()+1).orElse(1L);
  String raw=json(mapper.valueToTree(request));String number=request.number();
  var a=accounts.saveAndFlush(ExpertSettlementAccount.create(expert,revision,"****"+number.substring(number.length()-4),
    vault.seal("account:"+expert+":"+revision,raw)));
  return FinancialWorkflowResponse.account(a);
 }
 public FinancialWorkflowResponse readAccount(Long user,Long account) {
  role(user,"EXPERT",false);var a=accounts.findById(account).orElseThrow(this::missing);
  if(!a.getExpertProfileId().equals(profile(user)))throw forbidden();return FinancialWorkflowResponse.account(a);
 }
 @Transactional
 public FinancialWorkflowResponse preparePartner(Long user,FinancialWorkflowRequests.Enrollment request,String rawKey) {
  role(user,"EXPERT",true);review(FinancialGateway.Kind.PARTNER);Long expert=profile(user);
  var seller=sellers.findByExpertProfileId(expert).orElseThrow(this::missing);refresh(seller);
  if(seller.getVersion()!=request.expectedSellerVersion())throw stale();
  var account=currentAccount(expert);if(!account.getId().equals(request.accountId()))throw stale();
  var source=confirmed(seller,expert);
  String key=key("partner:"+seller.getId(),rawKey);
  var previous=operations.findBySellerDraftIdOrderByIdDesc(seller.getId());
  for(var old:previous) {
   if(old.getKeyHash().equals(key)) {
    if(!old.getAccountId().equals(account.getId()) || old.getSourceVersion()!=seller.getVersion())throw conflict();
    return FinancialWorkflowResponse.operation(old);
   }
   if(old.getState()==FinancialOperation.State.REQUESTED || old.getState()==FinancialOperation.State.UNKNOWN
     || old.getState()==FinancialOperation.State.PENDING || old.getState()==FinancialOperation.State.READY
     || old.getState()==FinancialOperation.State.BLOCKED && old.isSubmitClaimed())throw conflict();
   if(old.getAccountId().equals(account.getId()) && old.getSourceVersion()==seller.getVersion())return FinancialWorkflowResponse.operation(old);
  }
  ObjectNode payload=mapper.createObjectNode();payload.put("partnerId","capdi_partner_"+seller.getId());
  payload.put("contractId",policy.partnerContractId());payload.put("businessNumber",source.getBusinessNumber());
  payload.put("companyName",source.getCompanyName());payload.put("representativeName",source.getRepresentativeName());
  payload.set("details",parse(vault.open("account:"+expert+":"+account.getAccountRevision(),account.getEncryptedDetails())));
  payload.put("inputRevision",source.getInputRevision());payload.put("ocrRevision",source.getFile().getOcrRevision());
  previous.stream().filter(o->o.getState()==FinancialOperation.State.SUCCEEDED).findFirst()
    .ifPresent(o->payload.put("existingPartnerId",o.getExternalId()));
  return FinancialWorkflowResponse.operation(create(FinancialGateway.Kind.PARTNER,null,null,seller.getId(),account.getId(),null,null,
    seller.getVersion(),key,payload));
 }
 @Transactional
 public FinancialWorkflowResponse settlement(Long admin,Long payment) {
  role(admin,"ADMIN",true);var p=paid(payment);return FinancialWorkflowResponse.balance(balance(p));
 }
 public FinancialWorkflowResponse readBalance(Long admin,Long payment) {
  role(admin,"ADMIN",false);return FinancialWorkflowResponse.balance(balances.findByPaymentId(payment).orElseThrow(this::missing));
 }
 @Transactional
 public FinancialWorkflowResponse decideRefund(Long admin,Long refund,FinancialWorkflowRequests.RefundDecision request) {
  role(admin,"ADMIN",true);var r=refunds.findById(refund).orElseThrow(this::missing);var p=paid(r.getPaymentId());refresh(r);
  if(r.getVersion()!=request.expectedRefundVersion() || r.getState()!=PaymentRefundRequest.State.POLICY_HOLD
       && r.getState()!=PaymentRefundRequest.State.APPROVED)throw stale();
  if(request.decision()==FinancialApproval.Decision.APPROVED){r.approve();}
  else if(request.decision()==FinancialApproval.Decision.REJECTED)r.rejectReview();
  else r.holdReview();
  var b=balance(p);var approval=approvals.saveAndFlush(FinancialApproval.create(admin,p.getId(),r.getId(),null,b.getVersion(),r.getAmount(),
       FinancialApproval.Kind.REFUND,request.decision(),request.reason()));
  return FinancialWorkflowResponse.approval(approval);
 }
 @Transactional
 public FinancialWorkflowResponse prepareRefund(Long admin,Long refund,Long approvalId,String rawKey) {
  role(admin,"ADMIN",true);review(FinancialGateway.Kind.REFUND);
  var r=refunds.findById(refund).orElseThrow(this::missing);var p=paid(r.getPaymentId());refresh(r);
  String key=key("refund:"+refund,rawKey);
  var old=operations.findByRefundRequestId(refund);
  if(old.isPresent()) {if(!old.get().getKeyHash().equals(key) || !Objects.equals(old.get().getApprovalId(),approvalId))throw conflict();return FinancialWorkflowResponse.operation(old.get());}
  var approval=approvals.findById(approvalId).orElseThrow(this::missing);
  var latest=approvals.findByRefundRequestIdOrderByIdDesc(refund);
  if(latest.isEmpty() || !latest.getFirst().getId().equals(approvalId) || approval.getDecision()!=FinancialApproval.Decision.APPROVED
    || !Objects.equals(approval.getRefundRequestId(),refund) || !approval.getPaymentId().equals(p.getId()))throw stale();
  if(!operations.findByPaymentIdAndKindAndStateIn(p.getId(),FinancialGateway.Kind.REFUND,List.of(FinancialOperation.State.READY,FinancialOperation.State.REQUESTED,FinancialOperation.State.PENDING,FinancialOperation.State.UNKNOWN,FinancialOperation.State.BLOCKED)).stream().noneMatch(o->o.getState()==FinancialOperation.State.READY || o.isSubmitClaimed()))throw conflict();
  if(p.getMethod()==PaymentMethod.VIRTUAL_ACCOUNT)throw unavailable(); // no bank collection/retention agreement for client refunds.
  var b=balance(p);if(b.getRefunded().add(r.getAmount()).compareTo(b.getGross())>0)throw conflict();
  ObjectNode payload=paymentPayload(p);payload.put("amount",r.getAmount());payload.put("previousRefunded",b.getRefunded());
  r.requested();refunds.flush();
  return FinancialWorkflowResponse.operation(create(FinancialGateway.Kind.REFUND,p.getId(),refund,null,null,approvalId,null,
    r.getVersion(),key,payload));
 }
 @Transactional
 public FinancialWorkflowResponse decidePayout(Long admin,Long payment,FinancialWorkflowRequests.PayoutDecision request) {
  role(admin,"ADMIN",true);var p=paid(payment);var b=balance(p);
  if(b.getVersion()!=request.expectedBalanceVersion())throw stale();
  ExpertSettlementAccount a=null;
  if(request.accountId()!=null){
   a=currentAccount(p.getExpertProfileId());
   if(request.expectedAccountRevision()==null || !a.getId().equals(request.accountId()) || a.getAccountRevision()!=request.expectedAccountRevision())throw stale();
  }
  if(request.decision()!=FinancialApproval.Decision.APPROVED && !operations.findByPaymentIdAndKindAndStateIn(payment,FinancialGateway.Kind.PAYOUT,
    List.of(FinancialOperation.State.REQUESTED,FinancialOperation.State.PENDING,FinancialOperation.State.UNKNOWN,FinancialOperation.State.BLOCKED)).stream().noneMatch(FinancialOperation::isSubmitClaimed))throw conflict();
  if(request.decision()==FinancialApproval.Decision.APPROVED) {
   if(a==null)throw invalid();
   if(!operations.findByPaymentIdAndKindAndStateIn(payment,FinancialGateway.Kind.PAYOUT,List.of(FinancialOperation.State.READY,FinancialOperation.State.REQUESTED,FinancialOperation.State.PENDING,FinancialOperation.State.UNKNOWN,FinancialOperation.State.BLOCKED)).stream().noneMatch(o->o.getState()==FinancialOperation.State.READY || o.isSubmitClaimed()))throw conflict();
   review(FinancialGateway.Kind.PAYOUT);payoutEligible(p,b,a);
   if(request.amount().compareTo(b.available())>0)throw conflict();
  }
  b.review();balances.flush();
  var approval=approvals.saveAndFlush(FinancialApproval.create(admin,payment,null,a,b.getVersion(),request.amount(),
    FinancialApproval.Kind.PAYOUT,request.decision(),request.reason()));
  return FinancialWorkflowResponse.approval(approval);
 }
 @Transactional
 public FinancialWorkflowResponse preparePayout(Long admin,Long payment,Long approvalId,String rawKey) {
  role(admin,"ADMIN",true);review(FinancialGateway.Kind.PAYOUT);var p=paid(payment);var b=balance(p);
  var approval=approvals.findById(approvalId).orElseThrow(this::missing);
  String key=key("payout:"+payment,rawKey);
  var old=operations.findByApprovalId(approvalId);
  if(old.isPresent()){if(!old.get().getKeyHash().equals(key))throw conflict();return FinancialWorkflowResponse.operation(old.get());}
  var latest=approvals.findByPaymentIdOrderByIdDesc(payment).stream().filter(a->a.getKind()==FinancialApproval.Kind.PAYOUT).findFirst();
  var a=currentAccount(p.getExpertProfileId());
  if(!approval.getPaymentId().equals(payment) || approval.getKind()!=FinancialApproval.Kind.PAYOUT || latest.isEmpty()
    || !latest.get().getId().equals(approvalId) || approval.getDecision()!=FinancialApproval.Decision.APPROVED
    || approval.getBalanceVersion()!=b.getVersion() || !a.getId().equals(approval.getAccountId())
    || a.getAccountRevision()!=approval.getAccountRevision())throw stale();
  payoutEligible(p,b,a);b.reserve(approval.getAmount());balances.flush();
  ObjectNode payload=paymentPayload(p);payload.put("amount",approval.getAmount());payload.put("balanceVersion",b.getVersion());
  payload.put("accountRevision",a.getAccountRevision());payload.put("partnerId",registeredPartner(p.getExpertProfileId(),a));
  payload.set("account",parse(vault.open("account:"+a.getExpertProfileId()+":"+a.getAccountRevision(),a.getEncryptedDetails())));
  var o=create(FinancialGateway.Kind.PAYOUT,payment,null,null,a.getId(),approvalId,null,b.getVersion(),key,payload);
  movement(p.getId(),o.getId(),"reserve:"+o.getId(),Map.of(EXPERT_PAYABLE,approval.getAmount().negate(),PAYOUT_RESERVED,approval.getAmount()));
  return FinancialWorkflowResponse.operation(o);
 }
 @Transactional
 public FinancialWorkflowResponse saveParty(Long admin,FinancialWorkflowRequests.Party request) {
  role(admin,"ADMIN",true);sensitive();
  if(request.role()==BillingPartySnapshot.Role.PLATFORM) {if(request.subjectUserId()!=null)throw invalid();}
  else role(request.subjectUserId(),request.role().name(),false);
  validator.number(request.brn());validator.name(request.name());validator.name(request.representativeName());
  String context=partyContext(request.role(),request.subjectUserId(),admin);
  var p=parties.saveAndFlush(BillingPartySnapshot.create(admin,request.subjectUserId(),request.role(),
    vault.seal(context,json(mapper.valueToTree(request)))));
  return FinancialWorkflowResponse.builder().id(p.getId()).type("BILLING_PARTY_SNAPSHOT").build();
 }
 @Transactional
 public FinancialWorkflowResponse prepareInvoice(Long admin,Long draftId,FinancialWorkflowRequests.Invoice request) {
  role(admin,"ADMIN",true);review(FinancialGateway.Kind.TAX_INVOICE);
  var d=drafts.findById(draftId).orElseThrow(this::missing);
  var settlement=settlementDrafts.findById(d.getSettlementId()).orElseThrow(this::missing);var p=paid(settlement.getPaymentId());refresh(d);
  var supplier=parties.findById(request.supplierSnapshotId()).orElseThrow(this::missing);
  var recipient=parties.findById(request.recipientSnapshotId()).orElseThrow(this::missing);partyRoles(p,d,supplier,recipient);
  var sup=party(supplier);if(!policy.authorizedSupplierNumbers().contains(sup.path("brn").asText()))throw unavailable();
  var old=invoices.findByDraftId(draftId);
  if(old.isPresent()) {
   var i=old.get();if(!Objects.equals(i.getSupplierSnapshotId(),supplier.getId()) || !Objects.equals(i.getRecipientSnapshotId(),recipient.getId())
     || !Objects.equals(i.getOriginalInvoiceId(),request.originalInvoiceId()) || i.getModification()!=request.modification()
     || i.getTaxation()!=request.taxation() || !i.getWriteDate().equals(request.writeDate())
     || i.getSupply().compareTo(request.supply())!=0 || i.getVat().compareTo(request.vat())!=0)throw conflict();
   return FinancialWorkflowResponse.invoice(i);
  }
  BigDecimal total=request.supply().add(request.vat());
  if(request.originalInvoiceId()==null) {
   BigDecimal expected=d.getKind()==PaymentTaxInvoiceDraft.Kind.EXPERT_TO_CLIENT?balance(p).net():balance(p).platform();
   if(total.compareTo(expected)!=0 || request.supply().signum()<0 || request.vat().signum()<0 || d.getOriginalDraftId()!=null)throw invalid();
  } else {
   var original=invoices.findById(request.originalInvoiceId()).orElseThrow(this::missing);
   if(!original.isIssued() || original.getNtsState()!=FinancialGateway.NtsState.SUCCEEDED || !original.getPaymentId().equals(p.getId())
     || !Objects.equals(d.getOriginalDraftId(),original.getDraftId()) || !original.getSupplierSnapshotId().equals(supplier.getId())
     || !original.getRecipientSnapshotId().equals(recipient.getId()) || request.modification()==null)throw conflict();
   if(request.modification()==FinancialTaxInvoice.Modification.CANCELLATION_OF_CONTRACT
      && (request.supply().compareTo(original.getSupply().negate())!=0 || request.vat().compareTo(original.getVat().negate())!=0))throw invalid();
  }
  String scope=request.originalInvoiceId()==null?"BASE:"+p.getId()+":"+d.getKind():
    "CORRECTION:"+request.originalInvoiceId()+":"+request.modification();
  if(request.originalInvoiceId()!=null && request.modification()!=FinancialTaxInvoice.Modification.CANCELLATION_OF_CONTRACT
     && request.modification()!=FinancialTaxInvoice.Modification.DUPLICATE_ISSUANCE_DUE_TO_ERROR)scope+=":"+PaymentProcessingKeys.digest(json(mapper.valueToTree(request)));
  String scopeKey=PaymentProcessingKeys.digest(scope);
  if(invoices.findByIssuanceScopeKey(scopeKey).isPresent())throw conflict();
  try {
   var i=invoices.saveAndFlush(FinancialTaxInvoice.create(draftId,p.getId(),supplier.getId(),recipient.getId(),request.originalInvoiceId(),
     request.modification(),request.taxation(),request.writeDate(),request.supply(),request.vat(),scopeKey));
   return FinancialWorkflowResponse.invoice(i);
  }catch(IllegalArgumentException e){throw invalid();}
 }
 @Transactional
 public FinancialWorkflowResponse prepareInvoiceOperation(Long admin,Long invoiceId,String rawKey) {
  role(admin,"ADMIN",true);review(FinancialGateway.Kind.TAX_INVOICE);
  var invoice=invoices.findById(invoiceId).orElseThrow(this::missing);var p=paid(invoice.getPaymentId());refresh(invoice);
  try{invoice.claimExecutionOwner("LEGACY");}catch(IllegalStateException ex){throw conflict();}
  String key=key("invoice:"+invoiceId,rawKey);var old=operations.findByInvoiceId(invoiceId);
  if(old.isPresent()){if(!old.get().getKeyHash().equals(key))throw conflict();return FinancialWorkflowResponse.operation(old.get());}
  ObjectNode body=mapper.createObjectNode();body.put("amount",invoice.getTotal());body.put("currency","KRW");
  body.put("supply",invoice.getSupply());body.put("vat",invoice.getVat());body.put("taxation",invoice.getTaxation().name());
  body.put("writeDate",invoice.getWriteDate().toString());body.set("supplier",party(parties.findById(invoice.getSupplierSnapshotId()).orElseThrow(this::missing)));
  body.set("recipient",party(parties.findById(invoice.getRecipientSnapshotId()).orElseThrow(this::missing)));
  if(invoice.getOriginalInvoiceId()!=null) {
   var original=invoices.findById(invoice.getOriginalInvoiceId()).orElseThrow(this::missing);
   body.put("originalExternalId",original.getExternalId());body.put("modification",invoice.getModification().name());
  }
  body.put("taxPolicyVersion",policy.taxPolicyVersion());
  return FinancialWorkflowResponse.operation(create(FinancialGateway.Kind.TAX_INVOICE,p.getId(),null,null,null,null,invoiceId,
    invoice.getVersion(),key,body));
 }

 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public Run claimAdmin(Long admin,Long id,boolean lookup){role(admin,"ADMIN",false);return claim(id,lookup,admin);}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public Run claimWorker(Long id){return claim(id,true,null);}
 private Run claim(Long id,boolean lookup,Long actor) {
  var o=lockOperation(id);
  if(o.getKind()==FinancialGateway.Kind.TAX_INVOICE){var invoice=invoices.findById(o.getInvoiceId()).orElseThrow(this::missing);refresh(invoice);try{invoice.claimExecutionOwner("LEGACY");}catch(IllegalStateException ex){throw conflict();}}
  if(!o.identity().equals(gateway.identity())){
   if(o.getState()==FinancialOperation.State.READY)cancelReady(o,"PROVIDER_IDENTITY_CHANGED");
   else o.block("PROVIDER_IDENTITY_CHANGED",clock.instant());
   audit(o,actor,"PROVIDER_CHANGED");return new Run(id,null,null,o.getExternalId(),false);
  }
  if(lookup && !gateway.canLookup(o.getKind()) || !lookup && !gateway.canSubmit(o.getKind()))throw unavailable();
  if(!lookup) {
   review(o.getKind());
   if(o.getState()==FinancialOperation.State.READY && !writeSnapshotCurrent(o)) {
    cancelReady(o,"STALE_APPROVAL");audit(o,actor,"BLOCKED");return new Run(id,null,null,o.getExternalId(),false);
   }
  }
  String token=o.claim(lookup,clock.instant());
  if(token==null)return new Run(id,null,null,o.getExternalId(),false);
  operations.flush();audit(o,actor,lookup?"LOOKUP_CLAIM":"SUBMIT_CLAIM");
  String raw=vault.open("operation:"+o.getReference(),o.getEncryptedPayload());
  if(!vault.matchesFingerprint(o.getEncryptedPayload(),raw,o.getRequestHash()))throw conflict();
  return new Run(id,token,new FinancialGateway.Command(o.getReference(),o.getKind(),parse(raw)),o.getExternalId(),true);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public FinancialWorkflowResponse complete(Run run,FinancialGateway.Evidence proof) {
  var o=lockOperation(run.operation());if(!o.owns(run.token()))throw stale();
  if(!evidenceMatches(o,proof)) {o.uncertain("RESPONSE_MISMATCH",clock.instant());audit(o,null,"MISMATCH");return FinancialWorkflowResponse.operation(o);}
  var before=o.getState();o.observed(proof,clock.instant());
  if(o.getKind()==FinancialGateway.Kind.REFUND) {
   var r=refunds.findById(o.getRefundRequestId()).orElseThrow(this::missing);refresh(r);r.observed(proof.state());
   if(proof.state()==FinancialGateway.State.SUCCEEDED && before!=FinancialOperation.State.SUCCEEDED && !adjustments.existsByOperationId(o.getId()))
    applyRefund(o,r);
  } else if(o.getKind()==FinancialGateway.Kind.PAYOUT) {
   var b=balance(historicalPaid(o.getPaymentId()));BigDecimal amount=payload(o).path("amount").decimalValue();BigDecimal priorRecovery=b.getRecoverable();
   if(proof.state()==FinancialGateway.State.SUCCEEDED && before!=FinancialOperation.State.SUCCEEDED) {
    b.paid(amount);movement(o.getPaymentId(),o.getId(),"payout:"+o.getId(),Map.of(PAYOUT_RESERVED,amount.negate(),PAYOUT_PAID,amount));
    recoveryMovement(o,b,priorRecovery);
   } else if(proof.state()==FinancialGateway.State.FAILED && before!=FinancialOperation.State.FAILED) {
    b.release(amount);movement(o.getPaymentId(),o.getId(),"release:"+o.getId(),Map.of(PAYOUT_RESERVED,amount.negate(),EXPERT_PAYABLE,amount));
   }
  } else if(o.getKind()==FinancialGateway.Kind.PARTNER) {
   var a=accounts.findById(o.getAccountId()).orElseThrow(this::missing);refresh(a);
   a.proof(proof.bankState(),proof.kycVerified());
  } else {
   var invoice=invoices.findById(o.getInvoiceId()).orElseThrow(this::missing);refresh(invoice);invoice.bindEnvironment(o.getProviderEnvironment());invoice.observe(proof);
  }
  operations.flush();audit(o,null,"OBSERVED");return FinancialWorkflowResponse.operation(o);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public FinancialWorkflowResponse uncertain(Run run,String code) {
  var o=lockOperation(run.operation());if(!o.owns(run.token()))throw stale();
  o.uncertain(code,clock.instant());
  if(o.getRefundRequestId()!=null)refunds.findById(o.getRefundRequestId()).orElseThrow(this::missing).observed(FinancialGateway.State.UNKNOWN);
  audit(o,null,"UNCONFIRMED");return FinancialWorkflowResponse.operation(o);
 }
 @Transactional
 public FinancialWorkflowResponse cancelReservation(Long admin,Long id) {
  role(admin,"ADMIN",false);var o=lockOperation(id);
  if(o.getState()!=FinancialOperation.State.READY)throw conflict();
  cancelReady(o,"MANUAL_HOLD");audit(o,admin,"MANUAL_HOLD");return FinancialWorkflowResponse.operation(o);
 }
 @Transactional
 public FinancialWorkflowResponse manualLookup(Long admin,Long id) {
  role(admin,"ADMIN",false);var o=lockOperation(id);
  if(o.getState()==FinancialOperation.State.READY || o.busy(clock.instant()))throw conflict();
  o.manualReview(clock.instant());audit(o,admin,"MANUAL_LOOKUP");return FinancialWorkflowResponse.operation(o);
 }
 public List<Long> due(){return operations.findDue(clock.instant(),org.springframework.data.domain.PageRequest.of(0,50,org.springframework.data.domain.Sort.by("nextLookupAt").ascending().and(org.springframework.data.domain.Sort.by("id"))));}
 public FinancialWorkflowResponse readAdmin(Long admin,Long id){role(admin,"ADMIN",false);return FinancialWorkflowResponse.operation(operations.findById(id).orElseThrow(this::missing));}
 public FinancialWorkflowResponse readPartner(Long user,Long id){
  role(user,"EXPERT",false);var o=operations.findById(id).orElseThrow(this::missing);
  if(o.getKind()!=FinancialGateway.Kind.PARTNER)throw missing();
  var seller=sellers.findById(o.getSellerDraftId()).orElseThrow(this::missing);
  if(!profile(user).equals(seller.getExpertProfileId()))throw forbidden();
  return FinancialWorkflowResponse.operation(o);
 }
 public FinancialWorkflowResponse readInvoice(Long admin,Long id){role(admin,"ADMIN",false);return FinancialWorkflowResponse.invoice(invoices.findById(id).orElseThrow(this::missing));}
 public List<FinancialApproval> approvalHistory(Long admin,Long payment){role(admin,"ADMIN",false);payments.findById(payment).orElseThrow(this::missing);return approvals.findByPaymentIdOrderByIdDesc(payment);}

 public List<FinancialWorkflowResponse> accountHistory(Long user){
  role(user,"EXPERT",false);return accounts.findByExpertProfileIdOrderByAccountRevisionDesc(profile(user)).stream().map(FinancialWorkflowResponse::account).toList();
 }
 public List<FinancialHistoryResponse.Audit> auditHistory(Long admin,Long operation){
  readAdmin(admin,operation);return audits.findByOperationIdOrderByIdAsc(operation).stream().map(FinancialHistoryResponse.Audit::from).toList();
 }
 public List<FinancialHistoryResponse.Ledger> ledgerHistory(Long admin,Long payment){
  role(admin,"ADMIN",false);payments.findById(payment).orElseThrow(this::missing);
  return ledger.findByPaymentId(payment).stream().map(FinancialHistoryResponse.Ledger::from).toList();
 }
 public FinancialWorkflowResponse readClientRefund(Long user,Long payment,Long refund){
  role(user,"CLIENT",false);var p=payments.findById(payment).orElseThrow(this::missing);
  if(!p.getClientUserId().equals(user))throw forbidden();
  var r=refunds.findById(refund).orElseThrow(this::missing);if(!r.getPaymentId().equals(payment))throw missing();
  var result=FinancialWorkflowResponse.builder().id(refund).type("REFUND_REQUEST").paymentId(payment).version(r.getVersion())
    .state(r.getState().name()).amount(r.getAmount()).createdAt(r.getCreatedAt());
  var decisions=approvals.findByRefundRequestIdOrderByIdDesc(refund);
  if(!decisions.isEmpty())result.approvalDecision(decisions.getFirst().getDecision()).reasonCode(decisions.getFirst().getReasonCode());
  return result.build();
 }
 public Long paymentLookupTarget(Long admin,Long payment,Long attempt){
  role(admin,"ADMIN",false);var a=em.find(PaymentAttempt.class,attempt);if(a==null || !a.getPayment().getId().equals(payment))throw missing();return attempt;
 }
 private FinancialOperation lockOperation(Long id) {
  var o=operations.findById(id).orElseThrow(this::missing);
  if(o.getPaymentId()!=null) {var p=payments.lockById(o.getPaymentId()).orElseThrow(this::missing);refresh(p);}
  else {
   var seller=sellers.findById(o.getSellerDraftId()).orElseThrow(this::missing);
   var profile=em.find(ExpertProfile.class,seller.getExpertProfileId());role(profile.getUser().getId(),"EXPERT",true);refresh(seller);
  }
  refresh(o);return o;
 }
 private boolean writeSnapshotCurrent(FinancialOperation o) {
  if(o.getKind()==FinancialGateway.Kind.PAYOUT) {
   var p=historicalPaid(o.getPaymentId());var b=balance(p);var a=currentAccount(p.getExpertProfileId());
   if(!a.getId().equals(o.getAccountId()) || b.getVersion()!=o.getSourceVersion())return false;
   var latest=approvals.findByPaymentIdOrderByIdDesc(p.getId()).stream().filter(v->v.getKind()==FinancialApproval.Kind.PAYOUT).findFirst();
   if(latest.isEmpty() || !latest.get().getId().equals(o.getApprovalId()) || latest.get().getDecision()!=FinancialApproval.Decision.APPROVED)return false;
   try {payoutEligible(p,b,a);}catch(CustomException failure){return false;}
  } else if(o.getKind()==FinancialGateway.Kind.REFUND) {
   var r=refunds.findById(o.getRefundRequestId()).orElseThrow(this::missing);refresh(r);
   var latest=approvals.findByRefundRequestIdOrderByIdDesc(r.getId());
   if(latest.isEmpty() || !latest.getFirst().getId().equals(o.getApprovalId()) || latest.getFirst().getDecision()!=FinancialApproval.Decision.APPROVED)return false;
  } else if(o.getKind()==FinancialGateway.Kind.PARTNER) {
   var s=sellers.findById(o.getSellerDraftId()).orElseThrow(this::missing);
   if(s.getVersion()!=o.getSourceVersion() || !currentAccount(s.getExpertProfileId()).getId().equals(o.getAccountId()))return false;
   try {var source=confirmed(s,s.getExpertProfileId());var p=payload(o);
    if(source.getInputRevision()!=p.path("inputRevision").asLong() || source.getFile().getOcrRevision()!=p.path("ocrRevision").asLong())return false;
   }catch(CustomException e){return false;}
  } else {
   var data=payload(o);
   if(!Objects.equals(policy.taxPolicyVersion(),data.path("taxPolicyVersion").asText())
     || !policy.authorizedSupplierNumbers().contains(data.path("supplier").path("brn").asText()))return false;
  }
  return true;
 }
 private void cancelReady(FinancialOperation o,String code) {
  if(o.getKind()==FinancialGateway.Kind.PAYOUT) {
   var b=balance(historicalPaid(o.getPaymentId()));BigDecimal amount=payload(o).path("amount").decimalValue();b.release(amount);
   movement(o.getPaymentId(),o.getId(),"release:"+o.getId(),Map.of(PAYOUT_RESERVED,amount.negate(),EXPERT_PAYABLE,amount));
  }
  if(o.getKind()==FinancialGateway.Kind.REFUND)refunds.findById(o.getRefundRequestId()).orElseThrow(this::missing).observed(FinancialGateway.State.FAILED);
  o.block(code,clock.instant());
 }
 private boolean evidenceMatches(FinancialOperation o,FinancialGateway.Evidence e) {
  if(e==null || e.state()==null || e.bankState()==null || e.ntsState()==null || !"KRW".equals(e.currency()) || !o.getReference().equals(e.reference())
    || e.externalId()!=null && !e.externalId().matches("[A-Za-z0-9_.:-]{1,200}")
    || o.getExternalId()!=null && e.externalId()!=null && !o.getExternalId().equals(e.externalId())
    || e.state()==FinancialGateway.State.SUCCEEDED && e.externalId()==null)return false;
  var p=payload(o);
  if(o.getKind()!=FinancialGateway.Kind.PARTNER
    && (e.amount()==null || e.amount().compareTo(p.path("amount").decimalValue())!=0 || !"KRW".equals(e.currency())))return false;
  return o.getKind()!=FinancialGateway.Kind.PARTNER || e.accountVerified()==(e.bankState()==FinancialGateway.BankState.VERIFIED)
    && (e.externalId()==null || p.path("partnerId").asText().equals(e.externalId()));
 }
 private void applyRefund(FinancialOperation o,PaymentRefundRequest refund) {
  var p=paid(o.getPaymentId());var b=balance(p);
  for(var payout:operations.findByPaymentIdAndKindAndStateIn(p.getId(),FinancialGateway.Kind.PAYOUT,List.of(FinancialOperation.State.READY))){
   refresh(payout);if(payout.getState()==FinancialOperation.State.READY){cancelReady(payout,"REFUND_ADJUSTMENT");audit(payout,null,"REFUND_HOLD");}
  }
  BigDecimal before=b.net(),oldRecovery=b.getRecoverable();b.refund(refund.getAmount());
  adjustments.save(SettlementAdjustment.record(p.getId(),o.getId(),refund.getAmount(),before,b.net()));
  BigDecimal fee=refund.getAmount().multiply(b.getPlatformRate()).setScale(2,java.math.RoundingMode.UNNECESSARY);
  movement(p.getId(),o.getId(),"refund:"+o.getId(),Map.of(SETTLEMENT_CLEARING,refund.getAmount(),PLATFORM_FEE,fee.negate(),EXPERT_PAYABLE,refund.getAmount().subtract(fee).negate()));
  recoveryMovement(o,b,oldRecovery);
 }
 private void recoveryMovement(FinancialOperation o,SettlementBalance b,BigDecimal before) {
  BigDecimal delta=b.getRecoverable().subtract(before);
  if(delta.signum()!=0)movement(o.getPaymentId(),o.getId(),"recovery:"+o.getId(),Map.of(EXPERT_PAYABLE,delta,RECOVERY_RECEIVABLE,delta.negate()));
 }
 private SettlementBalance balance(Payment p) {
  var old=balances.findByPaymentId(p.getId());
  if(old.isPresent()){refresh(old.get());return old.get();}
  var b=balances.saveAndFlush(SettlementBalance.from(p));
  movement(p.getId(),null,"opening:"+p.getId(),Map.of(SETTLEMENT_CLEARING,p.getAmount().negate(),PLATFORM_FEE,b.platform(),EXPERT_PAYABLE,b.expert()));
  return b;
 }
 private void payoutEligible(Payment p,SettlementBalance b,ExpertSettlementAccount a) {
  if(b.isHeld() || !b.integralAllocation() || a.getState()!=ExpertSettlementAccount.State.VERIFIED || !a.isKycVerified()
    || refunds.findByPaymentId(p.getId()).stream().anyMatch(r->r.getState()!=PaymentRefundRequest.State.FAILED && r.getState()!=PaymentRefundRequest.State.SUCCEEDED && r.getState()!=PaymentRefundRequest.State.REJECTED))throw conflict();
  registeredPartner(p.getExpertProfileId(),a);
 }
 private String registeredPartner(Long expert,ExpertSettlementAccount account) {
  var seller=sellers.findByExpertProfileId(expert).orElseThrow(this::missing);refresh(seller);confirmed(seller,expert);
  return operations.findBySellerDraftIdOrderByIdDesc(seller.getId()).stream()
   .filter(o->o.getState()==FinancialOperation.State.SUCCEEDED && o.getAccountId().equals(account.getId()) && o.getSourceVersion()==seller.getVersion())
   .findFirst().map(FinancialOperation::getExternalId).orElseThrow(this::stale);
 }
 private ExpertSettlementAccount currentAccount(Long expert) {
  var owner=em.find(ExpertProfile.class,expert);if(owner==null)throw missing();role(owner.getUser().getId(),"EXPERT",true);
  var a=accounts.findFirstByExpertProfileIdOrderByAccountRevisionDesc(expert).orElseThrow(this::missing);refresh(a);return a;
 }
 private BusinessRegistrationInfo confirmed(ExpertSellerDraft seller,Long expert) {
  var source=business.findForUpdate(seller.getBusinessInfoId()).orElseThrow(this::missing);refresh(source);
  if(!source.getExpertProfile().getId().equals(expert))throw forbidden();
  var file=files.findForOcr(source.getFile().getId()).orElseThrow(this::missing);refresh(file);
  if(file.getOcrStatus()!=OcrStatusEnum.SUCCEEDED || file.getFileType()!=FileTypeEnum.BUSINESS_REGISTRATION
    || !source.isConfirmedFor(file.getId(),file.getOcrRevision()) || !seller.getFileId().equals(file.getId())
    || seller.getInputRevision()!=source.getInputRevision() || seller.getOcrRevision()!=file.getOcrRevision())throw stale();
  return source;
 }
 private Payment historicalPaid(Long id) {
  var p=payments.lockById(id).orElseThrow(this::missing);refresh(p);
  if(p.getStatus()!=PaymentStatus.PAID)throw conflict();return p;
 }
 private Payment paid(Long id) {
  var p=payments.lockById(id).orElseThrow(this::missing);refresh(p);
  if(p.getStatus()!=PaymentStatus.PAID || p.getActiveAttemptId()==null)throw conflict();
  var a=em.find(PaymentAttempt.class,p.getActiveAttemptId(),LockModeType.PESSIMISTIC_WRITE);refresh(a);
  if(a.getStatus()!=PaymentAttemptStatus.SUCCEEDED || a.getFailure()!=PaymentProcessingFailure.NONE)throw conflict();return p;
 }
 private void partyRoles(Payment p,PaymentTaxInvoiceDraft draft,BillingPartySnapshot supplier,BillingPartySnapshot recipient) {
  Long expertUser=em.find(ExpertProfile.class,p.getExpertProfileId()).getUser().getId();
  if(draft.getKind()==PaymentTaxInvoiceDraft.Kind.EXPERT_TO_CLIENT) {
   if(supplier.getRole()!=BillingPartySnapshot.Role.EXPERT || !Objects.equals(supplier.getSubjectUserId(),expertUser)
     || recipient.getRole()!=BillingPartySnapshot.Role.CLIENT || !Objects.equals(recipient.getSubjectUserId(),p.getClientUserId()))throw forbidden();
  } else if(supplier.getRole()!=BillingPartySnapshot.Role.PLATFORM || recipient.getRole()!=BillingPartySnapshot.Role.EXPERT
     || !Objects.equals(recipient.getSubjectUserId(),expertUser))throw forbidden();
 }
 private JsonNode party(BillingPartySnapshot p){return parse(vault.open(partyContext(p.getRole(),p.getSubjectUserId(),p.getCreatedBy()),p.getEncryptedDetails()));}
 private String partyContext(BillingPartySnapshot.Role role,Long subject,Long creator){return "party:"+role+":"+subject+":"+creator;}
 private ObjectNode paymentPayload(Payment p) {
  ObjectNode n=mapper.createObjectNode();var a=em.find(PaymentAttempt.class,p.getActiveAttemptId());
  n.put("paymentId","capdi_"+a.getAttemptKey());n.put("gross",p.getAmount());n.put("currency","KRW");n.put("method",p.getMethod().name());return n;
 }
 private FinancialOperation create(FinancialGateway.Kind kind,Long payment,Long refund,Long seller,Long account,Long approval,
   Long invoice,long source,String key,JsonNode payload) {
  String reference="fin_"+UUID.randomUUID(),raw=json(payload);
  var o=operations.saveAndFlush(FinancialOperation.create(reference,kind,gateway.identity(),payment,refund,seller,account,approval,invoice,source,key,
   vault.fingerprint(raw),vault.seal("operation:"+reference,raw),clock.instant()));
  audit(o,null,"CREATED");return o;
 }
 private void movement(Long payment,Long operation,String movement,Map<FinancialLedgerEntry.Bucket,BigDecimal> lines) {
  if(lines.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).signum()!=0)throw new IllegalStateException("Unbalanced journal");
  ledger.saveAllAndFlush(lines.entrySet().stream().map(e->FinancialLedgerEntry.line(payment,operation,movement,e.getKey(),e.getValue())).toList());
 }
 private JsonNode payload(FinancialOperation o){return parse(vault.open("operation:"+o.getReference(),o.getEncryptedPayload()));}
 private JsonNode parse(String json){try{return mapper.readTree(json);}catch(Exception e){throw unavailable();}}
 private String json(JsonNode n){try{return mapper.writeValueAsString(n);}catch(Exception e){throw unavailable();}}
 private String key(String scope,String key){return PaymentProcessingKeys.digest(scope+":"+PaymentProcessingKeys.requestKey(key));}
 private void refresh(Object entity){em.refresh(entity,LockModeType.PESSIMISTIC_WRITE);}
 private void audit(FinancialOperation o,Long actor,String action){audits.save(FinancialOperationAudit.record(o.getId(),actor,action,o.getState().name()));}
 private Long profile(Long user){return sources.expertProfileId(user).orElseThrow(this::missing);}
 private void sensitive(){if(!policy.sensitiveCollectionApproved() || !vault.available())throw unavailable();}
 private void review(FinancialGateway.Kind k){
  if(!policy.reviewed(k) || !gateway.canSubmit(k) || !vault.available()
    || k==FinancialGateway.Kind.PARTNER && (policy.partnerContractId()==null || policy.partnerContractId().isBlank() || "UNCONFIGURED".equals(policy.partnerContractId()))
    || k==FinancialGateway.Kind.TAX_INVOICE && ("UNREVIEWED".equals(policy.taxPolicyVersion()) || policy.authorizedSupplierNumbers().isEmpty()))throw unavailable();
 }
 private void role(Long user,String required,boolean lock){if(user==null)throw new CustomException(ErrorCode.UNAUTHORIZED);if(!required.equals(sources.userType(user,lock).orElse("")))throw forbidden();}
 private CustomException invalid(){return new CustomException(ErrorCode.INVALID_INPUT);}
 private CustomException forbidden(){return new CustomException(ErrorCode.FORBIDDEN);}
 private CustomException missing(){return new CustomException(ErrorCode.NOT_FOUND);}
 private CustomException conflict(){return new CustomException(ErrorCode.PAYMENT_EXECUTION_CONFLICT);}
 private CustomException stale(){return new CustomException(ErrorCode.PAYMENT_EXECUTION_STALE);}
 private CustomException unavailable(){return new CustomException(ErrorCode.PAYMENT_FINANCIAL_POLICY_UNCONFIRMED);}
}
