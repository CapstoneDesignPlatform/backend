package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.NotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {

    Optional<NotificationSubscription> findByExpertProfileId(Long expertProfileId);
}
