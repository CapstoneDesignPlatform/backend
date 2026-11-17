package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.ExpertProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.capdi.backend.domain.expert.entity.VerificationStatusEnum;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpertProfileRepository extends JpaRepository<ExpertProfile, Long> {

    Optional<ExpertProfile> findByUserId(Long userId);
    List<ExpertProfile> findByUserIdIn(Collection<Long> userIds);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE ExpertProfile p SET p.selectedCount = p.selectedCount + 1, p.updatedAt = :updatedAt " +
            "WHERE p.id = :profileId AND p.verificationStatus = :approved")
    int incrementSelectedCountIfApproved(
            @Param("profileId") Long profileId,
            @Param("approved") VerificationStatusEnum approved,
            @Param("updatedAt") LocalDateTime updatedAt);
}
