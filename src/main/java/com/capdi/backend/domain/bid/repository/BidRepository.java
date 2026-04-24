package com.capdi.backend.domain.bid.repository;

import com.capdi.backend.domain.bid.entity.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Bid 데이터 접근 Repository
 * 기본 CRUD 기능은 JpaRepository에서 제공된다.
 */
public interface BidRepository extends JpaRepository<Bid, Long> {

    /**
     * 특정 전문가 프로필이 등록한 입찰 목록 조회
     */
    List<Bid> findByExpertProfileId(Long expertProfileId);
}