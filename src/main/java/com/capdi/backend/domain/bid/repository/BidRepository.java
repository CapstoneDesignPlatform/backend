package com.capdi.backend.domain.bid.repository;

import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.bid.entity.BidStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bid b WHERE b.announcement = :announcement AND b.expertUser.id = :expertUserId")
    Optional<Bid> findByAnnouncementAndExpertUserIdWithLock(
            @Param("announcement") Announcement announcement, @Param("expertUserId") Long expertUserId);

    List<Bid> findAllByAnnouncement(Announcement announcement);
    List<Bid> findAllByAnnouncementIn(List<Announcement> announcements);
    List<Bid> findAllByAnnouncementInAndStatus(List<Announcement> announcements, BidStatusEnum status);
    Optional<Bid> findByIdAndAnnouncement(Long id, Announcement announcement);
    int countByAnnouncement(Announcement announcement);

    @Query(value = "SELECT COUNT(*) FROM bids WHERE announcement_code = :code FOR UPDATE", nativeQuery = true)
    long countByAnnouncementCodeWithLock(@Param("code") String code);

    /** 취소·완료되지 않은 공고에 달린 입찰 수. 잠금 읽기라 대기 중 커밋된 입찰도 보인다. */
    @Query(value = "SELECT COUNT(*) FROM bids b JOIN announcement a ON a.announcement_code = b.announcement_code "
            + "WHERE a.client_info_id = :clientInfoId AND a.status <> 'CANCELLED' "
            + "AND a.progress_step <> 'STEP_7_COMPLETED' FOR SHARE", nativeQuery = true)
    long countInProgressBidsByClientInfoIdWithLock(@Param("clientInfoId") Long clientInfoId);

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
