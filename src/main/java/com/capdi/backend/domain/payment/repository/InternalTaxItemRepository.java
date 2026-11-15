package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.InternalTaxItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InternalTaxItemRepository extends JpaRepository<InternalTaxItem,Long>{List<InternalTaxItem> findByDocumentIdOrderByLineNumber(Long document);}
