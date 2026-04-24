package com.capdi.backend.domain.jobpost.repository;

import com.capdi.backend.domain.jobpost.entity.JobPost;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JobPost 데이터 접근 Repository
 * 기본 CRUD 기능은 JpaRepository에서 제공된다.
 */
public interface JobPostRepository extends JpaRepository<JobPost, Long> {
}