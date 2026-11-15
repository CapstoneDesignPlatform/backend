package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.ExpertSettlementAccount;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ExpertSettlementAccountRepository extends JpaRepository<ExpertSettlementAccount,Long> { java.util.List<ExpertSettlementAccount> findByExpertProfileIdOrderByAccountRevisionDesc(Long expert);java.util.Optional<ExpertSettlementAccount> findFirstByExpertProfileIdOrderByAccountRevisionDesc(Long expert);}
