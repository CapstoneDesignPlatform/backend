package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.PaymentReconciliationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PaymentReconciliationJobRepository extends JpaRepository<PaymentReconciliationJob,Long> {
    java.util.List<PaymentReconciliationJob> findTop100ByStateInAndDueAtLessThanEqualOrderByDueAtAsc(
            java.util.Collection<PaymentReconciliationJob.State> states,java.time.Instant due);
    Optional<PaymentReconciliationJob> findByAttemptId(Long attemptId);
}
