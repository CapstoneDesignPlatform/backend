package com.capdi.backend.domain.bid.repository;

import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.bid.entity.BidStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.capdi.backend.domain.announcement.entity.Announcement;
import java.util.List;
import java.util.Optional;

public interface BidRepository extends JpaRepository<Bid, Long> {

    List<Bid> findByExpertUserId(Long expertUserId);

    Page<Bid> findByExpertUserId(Long expertUserId, Pageable pageable);

    Page<Bid> findByExpertUserIdAndStatus(Long expertUserId, BidStatusEnum status, Pageable pageable);

    boolean existsByAnnouncementAndExpertUserId(Announcement announcement, Long expertUserId);

    Optional<Bid> findByAnnouncementAndExpertUserId(Announcement announcement, Long expertUserId);

    List<Bid> findAllByAnnouncement(Announcement announcement);
    List<Bid> findAllByAnnouncementIn(List<Announcement> announcements);
    List<Bid> findAllByAnnouncementInAndStatus(List<Announcement> announcements, BidStatusEnum status);
    Optional<Bid> findByIdAndAnnouncement(Long id, Announcement announcement);
    int countByAnnouncement(Announcement announcement);

    @Modifying
    @Query("UPDATE Bid b SET b.status = :rejected " +
           "WHERE b.announcement = :announcement AND b.id != :bidId AND b.status = :pending")
    void rejectAllPendingExcept(
            @Param("announcement") Announcement announcement,
            @Param("bidId") Long bidId,
            @Param("rejected") BidStatusEnum rejected,
            @Param("pending") BidStatusEnum pending);

    @Query("SELECT b.announcement.id, COUNT(b) FROM Bid b WHERE b.announcement.id IN :announcementIds GROUP BY b.announcement.id")
    List<Object[]> countsByAnnouncementIds(@Param("announcementIds") List<Long> announcementIds);
}
