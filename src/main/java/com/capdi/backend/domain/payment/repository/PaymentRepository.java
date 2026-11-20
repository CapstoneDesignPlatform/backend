package com.capdi.backend.domain.payment.repository;

import com.capdi.backend.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> lockById(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.bidId = :bidId and p.selectionRound = :round")
    Optional<Payment> findByBidIdAndSelectionRoundForUpdate(@Param("bidId") Long bidId, @Param("round") long round);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.announcementId = :announcementId and p.selectionRound = :round order by p.id")
    List<Payment> findByAnnouncementAndRoundForUpdate(@Param("announcementId") Long announcementId, @Param("round") long round);
}
