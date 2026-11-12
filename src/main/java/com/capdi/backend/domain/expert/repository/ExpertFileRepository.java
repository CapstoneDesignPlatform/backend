package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.ExpertFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpertFileRepository extends JpaRepository<ExpertFile, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select f from ExpertFile f where f.id = :id")
    java.util.Optional<ExpertFile> findForOcr(@org.springframework.data.repository.query.Param("id") Long id);

}
