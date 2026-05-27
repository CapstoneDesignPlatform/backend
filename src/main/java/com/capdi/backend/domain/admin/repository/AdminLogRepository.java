package com.capdi.backend.domain.admin.repository;

import com.capdi.backend.domain.admin.entity.AdminLog;
import com.capdi.backend.domain.admin.entity.AdminTargetTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

    List<AdminLog> findByAdminId(Long adminId);
    List<AdminLog> findByTargetTypeAndTargetId(AdminTargetTypeEnum targetType, Long targetId);
}
