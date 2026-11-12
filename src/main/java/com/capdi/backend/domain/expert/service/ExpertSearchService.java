package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.dto.ExpertCertificateTypeResponse;
import com.capdi.backend.domain.expert.dto.ExpertSearchDetailResponse;
import com.capdi.backend.domain.expert.dto.ExpertSearchItemResponse;
import com.capdi.backend.domain.expert.dto.ExpertSearchRequest;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.repository.ExpertSearchRepository;
import com.capdi.backend.domain.expert.repository.ExpertCertificateTypeRow;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import com.capdi.backend.global.response.PageResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ExpertSearchService {

    private final ExpertSearchRepository expertSearchRepository;
    private final Clock clock;

    public ExpertSearchService(ExpertSearchRepository expertSearchRepository,
                               @Qualifier("expertSearchClock") Clock clock) {
        this.expertSearchRepository = expertSearchRepository;
        this.clock = clock;
    }

    public PageResponse<ExpertSearchItemResponse> search(ExpertSearchRequest request) {
        LocalDate today = LocalDate.now(clock);
        String keyword = request.getKeyword() == null ? "" : request.getKeyword().strip();
        var profiles = expertSearchRepository.search(request.getCategory(), keyword, request.getPage(), today);
        List<Long> ids = profiles.getContent().stream().map(ExpertProfile::getId).toList();
        var representatives = expertSearchRepository.findRepresentativeNames(ids);
        var certificates = certificateTypes(ids, today);

        List<ExpertSearchItemResponse> items = profiles.getContent().stream()
                .map(profile -> new ExpertSearchItemResponse(profile.getUser().getId(), profile.getBusinessName(),
                        representatives.get(profile.getId()), certificates.getOrDefault(profile.getId(), List.of())))
                .toList();
        return PageResponse.from(profiles, items);
    }

    public ExpertSearchDetailResponse getDetail(Long expertUserId) {
        if (expertUserId <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        LocalDate today = LocalDate.now(clock);
        ExpertProfile profile = expertSearchRepository.findPublicProfile(expertUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
        List<Long> ids = List.of(profile.getId());
        String representative = expertSearchRepository.findRepresentativeNames(ids).get(profile.getId());
        var certificates = certificateTypes(ids, today).getOrDefault(profile.getId(), List.of());
        String portfolio = profile.getPortfolioDescription();

        return new ExpertSearchDetailResponse(profile.getUser().getId(), profile.getUser().getName(),
                profile.getBusinessName(), representative, profile.getUser().getPhone(), certificates,
                portfolio == null || portfolio.isBlank() ? null : portfolio);
    }

    private Map<Long, List<ExpertCertificateTypeResponse>> certificateTypes(Collection<Long> ids, LocalDate today) {
        return expertSearchRepository.findValidCertificateTypes(ids, today).stream()
                .collect(Collectors.groupingBy(ExpertCertificateTypeRow::profileId,
                        Collectors.collectingAndThen(Collectors.toList(), rows -> rows.stream()
                                .map(ExpertCertificateTypeRow::type).distinct()
                                .sorted(Comparator.comparing(Enum::name))
                                .map(ExpertCertificateTypeResponse::from).toList())));
    }
}
