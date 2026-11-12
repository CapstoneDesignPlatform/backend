package com.capdi.backend.domain.announcement.repository;

import com.capdi.backend.domain.announcement.entity.Announcement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import com.capdi.backend.domain.announcement.entity.AnnouncementProgressStepEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Optional<Announcement> findByAnnouncementCode(String announcementCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Announcement a WHERE a.announcementCode = :code")
    Optional<Announcement> findByAnnouncementCodeWithLock(@Param("code") String code);

    List<Announcement> findAllByClientInfo(ClientInfo clientInfo);
    boolean existsByClientInfoAndIdNot(ClientInfo clientInfo, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Announcement a WHERE a.clientInfo = :clientInfo " +
            "AND a.status = :status AND a.progressStep = :step")
    List<Announcement> findAllForProgressUpdateWithLock(
            @Param("clientInfo") ClientInfo clientInfo,
            @Param("status") AnnouncementStatusEnum status,
            @Param("step") AnnouncementProgressStepEnum step);
    Page<Announcement> findAllByStatus(AnnouncementStatusEnum status, Pageable pageable);
    List<Announcement> findAllByClientInfoOrderByCreatedAtDesc(ClientInfo clientInfo);
}
