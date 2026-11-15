package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.ExpertSettlementProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ExpertSettlementProfileRepository extends JpaRepository<ExpertSettlementProfile,Long> {
 Optional<ExpertSettlementProfile> findFirstByExpertProfileIdOrderByRevisionDesc(Long expert);
 List<ExpertSettlementProfile> findByExpertProfileIdOrderByRevisionDesc(Long expert);
}
