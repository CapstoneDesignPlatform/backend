package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.InternalTaxExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InternalTaxExecutionRepository extends JpaRepository<InternalTaxExecution,Long>{
 @org.springframework.data.jpa.repository.Query("select e.id from InternalTaxExecution e where e.dispatched=true and e.lookupCount<8 and e.state<>com.capdi.backend.domain.payment.entity.InternalTaxExecution.State.FAILED and not(e.state=com.capdi.backend.domain.payment.entity.InternalTaxExecution.State.ISSUED and e.ntsState=com.capdi.backend.domain.payment.gateway.TaxInvoiceProvider.TransmissionState.SUCCEEDED) and (e.leaseUntil is null or e.leaseUntil<=:now) and (e.nextLookupAt is null or e.nextLookupAt<=:now)")
 List<Long> due(@org.springframework.data.repository.query.Param("now") java.time.Instant now,org.springframework.data.domain.Pageable page);
 Optional<InternalTaxExecution> findByDocumentId(Long document);
}
