package com.capdi.backend.domain.announcement.repository;

import com.capdi.backend.domain.announcement.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Optional<Announcement> findByAnnouncementCode(String announcementCode);

    long countByAnnouncementCodeStartingWith(String phoneDigits);

    List<Announcement> findAllByClientInfo(ClientInfo clientInfo);
    Page<Announcement> findAllByStatus(AnnouncementStatusEnum status, Pageable pageable);
}
