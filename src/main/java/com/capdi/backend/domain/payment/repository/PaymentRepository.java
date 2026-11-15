package com.capdi.backend.domain.payment.repository;

import com.capdi.backend.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.bidId = :bidId")
    Optional<Payment> findByBidIdForUpdate(@Param("bidId") Long bidId);
}
