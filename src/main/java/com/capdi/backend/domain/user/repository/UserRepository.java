package com.capdi.backend.domain.user.repository;

import com.capdi.backend.domain.user.entity.SocialProviderEnum;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.entity.UserTypeEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findBySocialProviderAndSocialId(SocialProviderEnum socialProvider, String socialId);

    boolean existsBySocialProviderAndSocialId(SocialProviderEnum socialProvider, String socialId);

    Page<User> findAllByUserType(UserTypeEnum userType, Pageable pageable);
}
