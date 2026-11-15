package com.capdi.backend.domain.payment.repository;

import com.capdi.backend.domain.payment.entity.PaymentIdempotencyKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PaymentIdempotencyKeyRepository extends JpaRepository<PaymentIdempotencyKey, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from PaymentIdempotencyKey k where k.clientUserId = :user and k.keyHash = :hash")
    Optional<PaymentIdempotencyKey> findForUpdate(@Param("user") Long user, @Param("hash") String hash);
}
