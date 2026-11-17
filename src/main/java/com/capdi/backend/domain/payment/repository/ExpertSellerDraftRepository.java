package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.ExpertSellerDraft;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ExpertSellerDraftRepository extends JpaRepository<ExpertSellerDraft,Long> { java.util.Optional<ExpertSellerDraft> findByExpertProfileId(Long expert); }
