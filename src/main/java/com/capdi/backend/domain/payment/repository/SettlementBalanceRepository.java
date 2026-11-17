package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.SettlementBalance;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SettlementBalanceRepository extends JpaRepository<SettlementBalance,Long> {
 @org.springframework.data.jpa.repository.Query("select b from SettlementBalance b, Payment p where p.id=b.paymentId and p.expertProfileId=:expert")
 org.springframework.data.domain.Page<SettlementBalance> findForExpert(@org.springframework.data.repository.query.Param("expert") Long expert,org.springframework.data.domain.Pageable pageable);
 java.util.Optional<SettlementBalance> findByPaymentId(Long payment);}
