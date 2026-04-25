package com.capdi.backend.domain.jobpost.repository;

import com.capdi.backend.domain.jobpost.entity.JobPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostRepository extends JpaRepository<JobPost, Long> {
}
