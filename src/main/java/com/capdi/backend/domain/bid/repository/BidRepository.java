package com.capdi.backend.domain.bid.repository;

import com.capdi.backend.domain.bid.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByExpertUserId(Long expertUserId);

    long countByAnnouncementId(Long announcementId);
}
