package com.capdi.backend.domain.client.repository;

import com.capdi.backend.domain.client.entity.ClientInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<ClientInfo, Long> {
}
