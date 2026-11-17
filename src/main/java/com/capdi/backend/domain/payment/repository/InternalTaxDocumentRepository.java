package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.InternalTaxDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InternalTaxDocumentRepository extends JpaRepository<InternalTaxDocument,Long>{
 Optional<InternalTaxDocument> findByScopeKey(String scope);
 List<InternalTaxDocument> findByPaymentIdOrderByIdDesc(Long payment);
}
