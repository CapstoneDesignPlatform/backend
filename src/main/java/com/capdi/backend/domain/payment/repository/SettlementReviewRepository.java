package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.SettlementReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SettlementReviewRepository extends JpaRepository<SettlementReview,Long> {
 Optional<SettlementReview> findByPaymentIdAndRequestKey(Long payment,String key);
 List<SettlementReview> findByPaymentIdOrderByIdDesc(Long payment);
}
