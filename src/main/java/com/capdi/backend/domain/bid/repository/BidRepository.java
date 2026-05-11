package com.capdi.backend.domain.bid.repository;

import com.capdi.backend.domain.bid.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;
import com.capdi.backend.domain.announcement.entity.Announcement;
import java.util.List;

public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByExpertUserId(Long expertUserId);

    long countByAnnouncementId(Long announcementId);

    List<Bid> findAllByAnnouncement(Announcement announcement);
    int countByAnnouncement(Announcement announcement);
}
