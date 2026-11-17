package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.domain.payment.util.PaymentProcessingKeys;
import com.capdi.backend.domain.expert.entity.*;
import com.capdi.backend.domain.expert.repository.*;
import com.capdi.backend.global.exception.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.Objects;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class FinancialReviewService {
    private final PaymentSourceRepository sources;
    private final PaymentRepository payments;
    private final PaymentRefundRequestRepository refunds;
    private final ExpertSellerDraftRepository sellers;
    private final PaymentSettlementDraftRepository settlements;
    private final PaymentTaxInvoiceDraftRepository taxes;
    private final BusinessRegistrationInfoRepository business;
    private final ExpertFileRepository files;
    private final EntityManager em;
    @Transactional
    public FinancialReviewResponse requestRefund(Long user,Long payment,String key,BigDecimal amount) {
        return createRefund(user,payment,key,amount,false);
    }
    @Transactional
    public FinancialReviewResponse requestOperationalRefund(Long user,Long payment,String key,BigDecimal amount) {
        return createRefund(user,payment,key,amount,true);
    }
    private FinancialReviewResponse createRefund(Long user,Long payment,String key,BigDecimal amount,boolean operational) {
        role(user,"CLIENT",true);var p=payment(payment,true);
        if(!p.getClientUserId().equals(user))throw forbidden();
        if(amount==null || amount.signum()<=0 || amount.stripTrailingZeros().scale()>0
                || amount.compareTo(p.getAmount())>0)throw new CustomException(ErrorCode.INVALID_INPUT);
        String hash=PaymentProcessingKeys.requestKey(key);
        var previous=refunds.findByPaymentIdAndRequestKeyHash(payment,hash);
        if(previous.isPresent()) {
            if(previous.get().getAmount().compareTo(amount)!=0)throw new CustomException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
            return refund(previous.get());
        }
        if(p.getStatus()!=PaymentStatus.PAID || !operational && settlements.findByPaymentId(payment).isPresent())throw conflict();
        BigDecimal reserved=refunds.findByPaymentId(payment).stream()
                .filter(r->r.getState()!=PaymentRefundRequest.State.FAILED && r.getState()!=PaymentRefundRequest.State.REJECTED)
                .map(PaymentRefundRequest::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(reserved.add(amount).compareTo(p.getAmount())>0)throw conflict();
        return refund(refunds.saveAndFlush(PaymentRefundRequest.draft(payment,hash,amount)));
    }
    public FinancialReviewResponse readRefund(Long user,Long payment,Long refundId) {
        role(user,"CLIENT",false);var p=payment(payment,false);
        if(!p.getClientUserId().equals(user))throw forbidden();
        var r=refunds.findById(refundId).orElseThrow(this::missing);
        if(!r.getPaymentId().equals(payment))throw missing();return refund(r);
    }
    @Transactional
    public FinancialReviewResponse saveSeller(Long user,Long info) {
        role(user,"EXPERT",true);
        var profile=sources.expertProfileId(user).orElseThrow(this::missing);
        var source=business.findForUpdate(info).orElseThrow(this::missing);em.refresh(source,LockModeType.PESSIMISTIC_WRITE);
        if(!profile.equals(source.getExpertProfile().getId()))throw forbidden();
        var file=files.findById(source.getFile().getId()).orElseThrow(this::missing);em.refresh(file,LockModeType.PESSIMISTIC_WRITE);
        if(!current(source,file))throw conflict();
        var draft=sellers.findByExpertProfileId(profile).orElseGet(()->ExpertSellerDraft.create(profile,info,
                source.getInputRevision(),file.getId(),file.getOcrRevision()));
        if(draft.getId()!=null)em.refresh(draft,LockModeType.PESSIMISTIC_WRITE);
        draft.replace(info,source.getInputRevision(),file.getId(),file.getOcrRevision());
        sellers.saveAndFlush(draft);return seller(draft,source,file);
    }
    public FinancialReviewResponse readSeller(Long user) {
        role(user,"EXPERT",false);Long profile=sources.expertProfileId(user).orElseThrow(this::missing);
        var draft=sellers.findByExpertProfileId(profile).orElseThrow(this::missing);
        var source=business.findById(draft.getBusinessInfoId()).orElseThrow(this::missing);
        return seller(draft,source,source.getFile());
    }
    @Transactional
    public FinancialReviewResponse createSettlement(Long admin,Long payment) {
        role(admin,"ADMIN",true);var p=payment(payment,true);
        var old=settlements.findByPaymentId(payment);if(old.isPresent())return settlement(old.get());
        if(p.getStatus()!=PaymentStatus.PAID || !refunds.findByPaymentId(payment).isEmpty())throw conflict();
        return settlement(settlements.saveAndFlush(PaymentSettlementDraft.create(p)));
    }
    public FinancialReviewResponse readSettlement(Long admin,Long id) {
        role(admin,"ADMIN",false);return settlement(settlements.findById(id).orElseThrow(this::missing));
    }
    @Transactional
    public FinancialReviewResponse createTax(Long admin,Long settlement,String key,PaymentTaxInvoiceDraft.Kind kind,Long original) {
        role(admin,"ADMIN",true);
        var d=settlements.findById(settlement).orElseThrow(this::missing);
        payment(d.getPaymentId(),true);em.refresh(d,LockModeType.PESSIMISTIC_WRITE);
        String hash=PaymentProcessingKeys.requestKey(key);
        var old=taxes.findBySettlementIdAndRequestKeyHash(settlement,hash);
        if(old.isPresent()) {
            if(old.get().getKind()!=kind || !Objects.equals(old.get().getOriginalDraftId(),original))
                throw new CustomException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
            return tax(old.get(),d);
        }
        if(kind==null)throw new CustomException(ErrorCode.INVALID_INPUT);
        if(original!=null) {
            var source=taxes.findById(original).orElseThrow(this::missing);
            if(!source.getSettlementId().equals(settlement) || source.getKind()!=kind)throw conflict();
        }
        return tax(taxes.saveAndFlush(PaymentTaxInvoiceDraft.create(settlement,hash,kind,original)),d);
    }
    public FinancialReviewResponse readTax(Long admin,Long id) {
        role(admin,"ADMIN",false);var t=taxes.findById(id).orElseThrow(this::missing);
        return tax(t,settlements.findById(t.getSettlementId()).orElseThrow(this::missing));
    }
    // No runtime adapter can release these holds until contractual/tax policies are reviewed.
    public void approvePayout(Long admin,Long settlement) {
        role(admin,"ADMIN",false);settlements.findById(settlement).orElseThrow(this::missing);
        throw new CustomException(ErrorCode.PAYMENT_FINANCIAL_POLICY_UNCONFIRMED);
    }
    public void issueTax(Long admin,Long invoice) {
        role(admin,"ADMIN",false);taxes.findById(invoice).orElseThrow(this::missing);
        throw new CustomException(ErrorCode.PAYMENT_FINANCIAL_POLICY_UNCONFIRMED);
    }
    private Payment payment(Long id,boolean lock) {
        var p=(lock?payments.lockById(id):payments.findById(id)).orElseThrow(this::missing);
        if(lock)em.refresh(p,LockModeType.PESSIMISTIC_WRITE);return p;
    }
    private void role(Long user,String role,boolean lock) {
        if(user==null)throw new CustomException(ErrorCode.UNAUTHORIZED);
        if(!role.equals(sources.userType(user,lock).orElse("")))throw forbidden();
    }
    private boolean current(BusinessRegistrationInfo b,ExpertFile f) {
        return f.getFileType()==FileTypeEnum.BUSINESS_REGISTRATION && f.getOcrStatus()==OcrStatusEnum.SUCCEEDED
                && b.isConfirmedFor(f.getId(),f.getOcrRevision());
    }
    private FinancialReviewResponse refund(PaymentRefundRequest r) {
        return FinancialReviewResponse.builder().id(r.getId()).version(r.getVersion()).type("REFUND_REQUEST").paymentId(r.getPaymentId())
                .state(r.getState().name()).gross(r.getAmount()).externalExecuted(false).build();
    }
    private FinancialReviewResponse seller(ExpertSellerDraft d,BusinessRegistrationInfo b,ExpertFile f) {
        boolean current=current(b,f) && d.getInputRevision()==b.getInputRevision() && d.getOcrRevision()==f.getOcrRevision()
                && d.getFileId().equals(f.getId());
        return FinancialReviewResponse.builder().id(d.getId()).version(d.getVersion()).type("SELLER_DRAFT").state(d.getState().name())
                .snapshotCurrent(current).externalExecuted(false).build();
    }
    private FinancialReviewResponse settlement(PaymentSettlementDraft d) {
        return FinancialReviewResponse.builder().id(d.getId()).type("SETTLEMENT_DRAFT").paymentId(d.getPaymentId())
                .state(d.getState().name()).gross(d.getGross()).platformAmount(d.getPlatformAmount())
                .expertAmount(d.getExpertAmount()).externalExecuted(false).build();
    }
    private FinancialReviewResponse tax(PaymentTaxInvoiceDraft t,PaymentSettlementDraft d) {
        return FinancialReviewResponse.builder().id(t.getId()).type(t.getKind().name()).paymentId(d.getPaymentId())
                .state(t.getState().name()).externalExecuted(false).build();
    }
    private CustomException forbidden(){return new CustomException(ErrorCode.FORBIDDEN);}
    private CustomException missing(){return new CustomException(ErrorCode.NOT_FOUND);}
    private CustomException conflict(){return new CustomException(ErrorCode.PAYMENT_EXECUTION_CONFLICT);}
}
