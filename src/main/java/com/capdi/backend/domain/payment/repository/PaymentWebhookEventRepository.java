package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent,Long> {
    Optional<PaymentWebhookEvent> findByProviderCodeAndMerchantIdAndEnvironmentAndEventKeyHash(
            String code,String merchant,PaymentEnvironment env,String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from PaymentWebhookEvent e where e.id=:id")
    Optional<PaymentWebhookEvent> lockById(@Param("id") Long id);
}
