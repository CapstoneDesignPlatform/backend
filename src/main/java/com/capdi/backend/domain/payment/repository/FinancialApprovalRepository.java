package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.FinancialApproval;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FinancialApprovalRepository extends JpaRepository<FinancialApproval,Long> {
 java.util.List<FinancialApproval> findByRefundRequestIdOrderByIdDesc(Long refund);
 java.util.List<FinancialApproval> findByPaymentIdOrderByIdDesc(Long payment);
 java.util.Optional<FinancialApproval> findBySettlementReviewId(Long reviewId);
}
