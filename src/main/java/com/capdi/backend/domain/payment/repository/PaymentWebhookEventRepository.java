package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent,Long> {
    Optional<PaymentWebhookEvent> findByProviderCodeAndProviderAccountIdAndEnvironmentAndEventKeyHash(
            String code,String providerAccountId,PaymentEnvironment env,String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from PaymentWebhookEvent e where e.id=:id")
    Optional<PaymentWebhookEvent> lockById(@Param("id") Long id);
    @Query(value="select id from payment_webhook_events where dead_letter=false and next_attempt_at<=:now and (state='RECEIVED' or (state='PROCESSING' and lease_until<=:now)) order by next_attempt_at,id limit :batch",nativeQuery=true)
    List<Long> findDueIds(@Param("now") java.time.Instant now,@Param("batch") int batch);
}
