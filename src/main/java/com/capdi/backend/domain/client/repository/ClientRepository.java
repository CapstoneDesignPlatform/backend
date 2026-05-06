package com.capdi.backend.domain.client.repository;

import com.capdi.backend.domain.client.entity.ClientInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<ClientInfo, Long> {
    Optional<ClientInfo> findByUser_Id(Long userId);
    boolean existsByUser_Id(Long userId);
}
