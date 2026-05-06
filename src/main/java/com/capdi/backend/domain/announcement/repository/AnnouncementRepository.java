package com.capdi.backend.domain.announcement.repository;

import com.capdi.backend.domain.announcement.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Optional<Announcement> findByAnnouncementCode(String announcementCode);

    long countByAnnouncementCodeStartingWith(String phoneDigits);
}
