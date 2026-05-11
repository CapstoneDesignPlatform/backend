package com.capdi.backend.domain.announcement.service;

import com.capdi.backend.domain.announcement.dto.AnnouncementCreateRequest;
import com.capdi.backend.domain.announcement.dto.AnnouncementCreateResponse;
import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.repository.AnnouncementRepository;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.client.repository.ClientRepository;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    private static final int MAX_RETRY_COUNT = 3;

    @Lazy
    @Autowired
    private AnnouncementService self;

    public AnnouncementCreateResponse createAnnouncement(AnnouncementCreateRequest request, Long userId) {
        ClientInfo clientInfo = findClientInfo(request.getClientInfoId());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return generateAndSave(request, user, clientInfo);
    }

    public AnnouncementCreateResponse createGuestAnnouncement(AnnouncementCreateRequest request) {
        ClientInfo clientInfo = findClientInfo(request.getClientInfoId());
        return generateAndSave(request, null, clientInfo);
    }

    private AnnouncementCreateResponse generateAndSave(
            AnnouncementCreateRequest request, User user, ClientInfo clientInfo) {

        for (int attempt = 0; attempt < MAX_RETRY_COUNT; attempt++) {
            try {
                String code = generateAnnouncementCode(clientInfo.getContact());
                return self.saveAnnouncement(request, user, clientInfo, code);
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_RETRY_COUNT - 1) {
                    throw new CustomException(ErrorCode.CODE_GENERATION_FAILED);
                }
            }
        }
        throw new CustomException(ErrorCode.CODE_GENERATION_FAILED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnnouncementCreateResponse saveAnnouncement(
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
                .assetScale(request.getAssetSize())
                .diagnosisReason(request.getDiagnosisReason())
                .diagnosisReasonDetail(request.getDiagnosisReasonDetail())
                .build();

        announcementRepository.saveAndFlush(announcement);
        return AnnouncementCreateResponse.of(code);
    }

    private String generateAnnouncementCode(String contact) {
        String digits = contact.replaceAll("[^0-9]", "");
        long count = announcementRepository.countByAnnouncementCodeStartingWith(digits);
        return digits + String.format("%02d", count + 1);
    }

    private ClientInfo findClientInfo(Long clientInfoId) {
        return clientRepository.findById(clientInfoId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLIENT_INFO_NOT_FOUND));
    }
}
