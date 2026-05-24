package com.capdi.backend.domain.bid.repository;

import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.bid.entity.BidStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.capdi.backend.domain.announcement.entity.Announcement;
import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByExpertUserId(Long expertUserId);

    Page<Bid> findByExpertUserId(Long expertUserId, Pageable pageable);

    Page<Bid> findByExpertUserIdAndStatus(Long expertUserId, BidStatusEnum status, Pageable pageable);

    boolean existsByAnnouncementAndExpertUserId(Announcement announcement, Long expertUserId);

    Optional<Bid> findByAnnouncementAndExpertUserId(Announcement announcement, Long expertUserId);

    long countByAnnouncementId(Long announcementId);

    List<Bid> findAllByAnnouncement(Announcement announcement);
    int countByAnnouncement(Announcement announcement);
}
