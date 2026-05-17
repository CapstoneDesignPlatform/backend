package com.capdi.backend.domain.admin.repository;

import com.capdi.backend.domain.admin.entity.AdminLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {
}
