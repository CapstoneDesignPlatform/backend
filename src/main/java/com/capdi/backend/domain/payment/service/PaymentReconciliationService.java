package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.PaymentReconciliationJob;
import com.capdi.backend.domain.payment.repository.PaymentReconciliationJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.util.List;

/** No scheduler is enabled. This recovery entry point only fetches existing transactions; never prepares/confirms. */
@Service @RequiredArgsConstructor @Transactional(propagation=Propagation.NOT_SUPPORTED)
public class PaymentReconciliationService {
    private final PaymentReconciliationJobRepository jobs;
    private final PaymentExecutionService execution;
    private final Clock clock;
    private final PaymentLookupLeaseService leases;
    public int runDue() {
        int processed=0;
        var due=jobs.findTop100ByStateInAndDueAtLessThanEqualOrderByDueAtAsc(
                List.of(PaymentReconciliationJob.State.PENDING,PaymentReconciliationJob.State.RUNNING),clock.instant());
        for(var job:due) {
            var lease=leases.claim(job.getId());if(!lease.run())continue;
            var failure=com.capdi.backend.domain.payment.entity.PaymentProcessingFailure.PROVIDER_ERROR;
            try {var result=execution.reconcileInternal(job.getAttemptId());failure=result.getFailure();processed++;}
            catch(RuntimeException ignored) { }
            finally {leases.finish(lease,failure);}
        }
        return processed;
    }
}
