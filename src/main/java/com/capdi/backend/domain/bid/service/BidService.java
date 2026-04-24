package com.capdi.backend.domain.bid.service;

import com.capdi.backend.domain.bid.dto.BidCreateRequest;
import com.capdi.backend.domain.bid.dto.BidResponse;
import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.bid.repository.BidRepository;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.domain.jobpost.entity.JobPost;
import com.capdi.backend.domain.jobpost.repository.JobPostRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

    private final BidRepository bidRepository;
    private final ExpertProfileRepository expertProfileRepository;
    private final JobPostRepository jobPostRepository;

    @Transactional
    public BidResponse createBid(BidCreateRequest request) {
        ExpertProfile expertProfile = expertProfileRepository.findById(request.getExpertProfileId())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        JobPost jobPost = jobPostRepository.findById(request.getJobPostId())
                .orElseThrow(() -> new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        Bid bid = Bid.builder()
                .expertProfile(expertProfile)
                .jobPost(jobPost)
                .proposedPrice(request.getProposedPrice())
                .proposal(request.getProposal())
                .build();

        return BidResponse.from(bidRepository.save(bid));
    }

    public List<BidResponse> getBidsByExpertProfile(Long expertProfileId) {
        return bidRepository.findByExpertProfileId(expertProfileId)
                .stream()
                .map(BidResponse::from)
                .toList();
    }
}