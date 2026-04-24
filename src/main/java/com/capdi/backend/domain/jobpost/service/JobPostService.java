package com.capdi.backend.domain.jobpost.service;

import com.capdi.backend.domain.company.entity.Company;
import com.capdi.backend.domain.company.repository.CompanyRepository;
import com.capdi.backend.domain.jobpost.dto.JobPostCreateRequest;
import com.capdi.backend.domain.jobpost.dto.JobPostResponse;
import com.capdi.backend.domain.jobpost.entity.JobPost;
import com.capdi.backend.domain.jobpost.repository.JobPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobPostService {

    private final JobPostRepository jobPostRepository;
    private final CompanyRepository companyRepository;

    /**
     * 전체 공고 목록 조회
     */
    public List<JobPostResponse> getJobPosts() {
        return jobPostRepository.findAll()
                .stream()
                .map(JobPostResponse::from)
                .toList();
    }

    /**
     * 특정 공고 상세 조회
     */
    public JobPostResponse getJobPost(Long jobPostId) {
        return jobPostRepository.findById(jobPostId)
                .map(JobPostResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("해당 공고를 찾을 수 없습니다. id=" + jobPostId));
    }

    /**
     * 공고 등록
     */
    public JobPostResponse createJobPost(JobPostCreateRequest request) {
        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("해당 회사를 찾을 수 없습니다. id=" + request.getCompanyId()));

        JobPost jobPost = JobPost.builder()
                .company(company)
                .title(request.getTitle())
                .description(request.getDescription())
                .budget(request.getBudget())
                .deadline(request.getDeadline())
                .build();

        JobPost savedJobPost = jobPostRepository.save(jobPost);
        return JobPostResponse.from(savedJobPost);
    }
}