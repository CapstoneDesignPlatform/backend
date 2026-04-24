package com.capdi.backend.domain.user.repository;

import com.capdi.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * User 데이터 접근 Repository
 * 기본 CRUD 기능은 JpaRepository에서 제공된다.
 */
public interface UserRepository extends JpaRepository<User, Long> {
}