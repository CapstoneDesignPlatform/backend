package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.SettlementProfileReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SettlementProfileReviewRepository extends JpaRepository<SettlementProfileReview,Long>{
 Optional<SettlementProfileReview> findByProfileIdAndRequestKey(Long profile,String key);
 List<SettlementProfileReview> findByProfileIdOrderByIdDesc(Long profile);
}
