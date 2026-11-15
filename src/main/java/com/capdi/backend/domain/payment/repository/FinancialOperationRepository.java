package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.FinancialOperation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FinancialOperationRepository extends JpaRepository<FinancialOperation,Long> {
 @org.springframework.data.jpa.repository.Query("select o.id from FinancialOperation o where o.submitClaimed=true and o.lookupCount<8 and o.nextLookupAt<=:now and (o.leaseUntil is null or o.leaseUntil<=:now) and (o.state in (com.capdi.backend.domain.payment.entity.FinancialOperation.State.REQUESTED,com.capdi.backend.domain.payment.entity.FinancialOperation.State.PENDING,com.capdi.backend.domain.payment.entity.FinancialOperation.State.UNKNOWN) or (o.state=com.capdi.backend.domain.payment.entity.FinancialOperation.State.SUCCEEDED and o.kind=com.capdi.backend.domain.payment.gateway.FinancialGateway.Kind.TAX_INVOICE and o.ntsState<>com.capdi.backend.domain.payment.gateway.FinancialGateway.NtsState.SUCCEEDED))")
 java.util.List<Long> findDue(@org.springframework.data.repository.query.Param("now") java.time.Instant now,org.springframework.data.domain.Pageable page);
 java.util.List<FinancialOperation> findByAccountIdAndKindAndStateIn(Long account,com.capdi.backend.domain.payment.gateway.FinancialGateway.Kind kind,java.util.Collection<FinancialOperation.State> states);
 java.util.List<FinancialOperation> findByPaymentIdAndKindAndStateIn(Long payment,com.capdi.backend.domain.payment.gateway.FinancialGateway.Kind kind,java.util.Collection<FinancialOperation.State> states);java.util.Optional<FinancialOperation> findByRefundRequestId(Long refund);
 java.util.Optional<FinancialOperation> findByApprovalId(Long approval);
 java.util.Optional<FinancialOperation> findByInvoiceId(Long invoice);
 java.util.List<FinancialOperation> findBySellerDraftIdOrderByIdDesc(Long seller);
 java.util.List<FinancialOperation> findTop50ByStateInAndNextLookupAtLessThanEqualOrderByNextLookupAtAsc(java.util.Collection<FinancialOperation.State> states,java.time.Instant now);}
