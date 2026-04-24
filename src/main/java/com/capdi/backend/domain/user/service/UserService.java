package com.capdi.backend.domain.user.service;

import com.capdi.backend.domain.user.dto.UserCreateRequest;
import com.capdi.backend.domain.user.dto.UserResponse;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 사용자 관련 비즈니스 로직 처리 Service
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 사용자 생성
     */
    public UserResponse createUser(UserCreateRequest request) {
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(request.getPasswordHash())
                .name(request.getName())
                .phone(request.getPhone())
                .role(request.getRole())
                .status(request.getStatus())
                .build();

        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    /**
     * 사용자 목록 조회
     */
    public List<UserResponse> getUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * 사용자 단건 조회
     */
    public UserResponse getUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자를 찾을 수 없습니다. id=" + userId));
    }
}