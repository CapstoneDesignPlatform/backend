package com.capdi.backend.domain.announcement.service;

import com.capdi.backend.domain.announcement.dto.AnnouncementCreateRequest;
import com.capdi.backend.domain.announcement.dto.AnnouncementCreateResponse;
import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementProgressStepEnum;
import com.capdi.backend.domain.announcement.repository.AnnouncementRepository;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.client.dto.ClientInfoRequest;
import com.capdi.backend.domain.client.repository.ClientRepository;
import com.capdi.backend.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnnouncementSaverService {

    private final AnnouncementRepository announcementRepository;
    private final ClientRepository clientRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnnouncementCreateResponse save(
            AnnouncementCreateRequest request, User user, ClientInfo clientInfo, String code) {
        return persistAnnouncement(request, user, clientInfo, code);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnnouncementCreateResponse saveGuest(
            AnnouncementCreateRequest request, ClientInfoRequest clientRequest, String code) {
        ClientInfo clientInfo = clientRepository.save(ClientInfo.builder()
                .user(null)
                .representativeName(clientRequest.getRepresentativeName())
                .contact(clientRequest.getContact())
                .email(clientRequest.getEmail())
                .companyName(clientRequest.getCompanyName())
                .businessNumber(clientRequest.getBusinessNumber())
                .address(clientRequest.getAddress())
                .website(clientRequest.getWebsite())
                .description(clientRequest.getDescription())
                .build());

        return persistAnnouncement(request, null, clientInfo, code);
    }

    private AnnouncementCreateResponse persistAnnouncement(
            AnnouncementCreateRequest request, User user, ClientInfo clientInfo, String code) {
        Announcement announcement = Announcement.builder()
                .user(user)
                .clientInfo(clientInfo)
                .announcementCode(code)
                .industry(request.getIndustry())
                .purpose(request.getPurpose())
                .businessOwnerType(request.getBusinessOwnerType())
                .category(request.getCategory())
                .currentIndustry(request.getCurrentIndustryStatus())
                .currentIndustryDetail(request.getCurrentIndustryDetail())
                .currentLicense(request.getHeldLicense())
                .jobType(request.getPurpose().toJobType())
                .requiredLicense(request.getRequiredLicense())
                .capital(request.getCapital())
                .capitalScale(request.getCapitalScale())
                .diagnosisReason(request.getDiagnosisReason())
                .diagnosisReasonDetail(request.getDiagnosisReasonDetail())
                .build();

        if (clientInfo.isComplete()) {
            announcement.updateProgressStep(AnnouncementProgressStepEnum.STEP_2_BID_CLOSED);
        }

        announcementRepository.saveAndFlush(announcement);
        return AnnouncementCreateResponse.of(code);
    }
}
