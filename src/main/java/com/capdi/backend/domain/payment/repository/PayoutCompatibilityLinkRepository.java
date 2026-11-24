package com.capdi.backend.domain.payment.repository;

import com.capdi.backend.domain.payment.entity.PayoutCompatibilityLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PayoutCompatibilityLinkRepository extends JpaRepository<PayoutCompatibilityLink, Long> {
    Optional<PayoutCompatibilityLink> findByReviewId(Long reviewId);
    Optional<PayoutCompatibilityLink> findByApprovalId(Long approvalId);
    List<PayoutCompatibilityLink> findByPaymentIdOrderByIdDesc(Long paymentId);
}
