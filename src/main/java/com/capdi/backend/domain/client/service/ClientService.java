package com.capdi.backend.domain.client.service;

import com.capdi.backend.domain.announcement.entity.AnnouncementProgressStepEnum;
import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import com.capdi.backend.domain.announcement.repository.AnnouncementRepository;
import com.capdi.backend.domain.client.dto.ClientInfoRequest;
import com.capdi.backend.domain.client.dto.ClientInfoResponse;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.client.repository.ClientRepository;
import com.capdi.backend.domain.bid.repository.BidRepository;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final AnnouncementRepository announcementRepository;
    private final BidRepository bidRepository;

    @Transactional
    public ClientInfoResponse saveGuestClientInfo(ClientInfoRequest request) {
        ClientInfo clientInfo = ClientInfo.builder()
                .user(null)
                .representativeName(request.getRepresentativeName())
                .contact(request.getContact())
                .email(request.getEmail())
                .companyName(request.getCompanyName())
                .businessNumber(request.getBusinessNumber())
                .address(request.getAddress())
                .website(request.getWebsite())
                .description(request.getDescription())
                .build();

        return ClientInfoResponse.from(clientRepository.save(clientInfo));
    }

    @Transactional
    public ClientInfoResponse saveClientInfo(Long userId, ClientInfoRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (clientRepository.existsByUser_Id(userId)) {
            throw new CustomException(ErrorCode.CLIENT_INFO_ALREADY_EXISTS);
        }

        ClientInfo clientInfo = ClientInfo.builder()
                .user(user)
                .representativeName(request.getRepresentativeName())
                .contact(request.getContact())
                .email(request.getEmail())
                .companyName(request.getCompanyName())
                .businessNumber(request.getBusinessNumber())
                .address(request.getAddress())
                .website(request.getWebsite())
                .description(request.getDescription())
                .build();

        return ClientInfoResponse.from(clientRepository.save(clientInfo));
    }

    @Transactional
    public ClientInfoResponse updateClientInfo(Long userId, ClientInfoRequest request) {
        ClientInfo clientInfo = clientRepository.findByUser_Id(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLIENT_INFO_NOT_FOUND));

        applyClientInfoUpdate(clientInfo, request);

        if (clientInfo.isComplete()) {
            announcementRepository.findAllForProgressUpdateWithLock(
                            clientInfo, AnnouncementStatusEnum.ACTIVE, AnnouncementProgressStepEnum.STEP_1_REGISTERED)
                    .forEach(announcement -> announcement.updateProgressStep(AnnouncementProgressStepEnum.STEP_2_BID_CLOSED));
        }

        return ClientInfoResponse.from(clientInfo);
    }

    @Transactional
    public ClientInfoResponse updateGuestClientInfo(String announcementCode, ClientInfoRequest request) {
        Announcement announcement = announcementRepository.findByAnnouncementCodeWithLock(announcementCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        if (!announcement.isGuest()) {
            throw new CustomException(ErrorCode.ANNOUNCEMENT_FORBIDDEN);
        }

        if (announcement.getStatus() != AnnouncementStatusEnum.ACTIVE
                || (announcement.getProgressStep() != AnnouncementProgressStepEnum.STEP_1_REGISTERED
                    && announcement.getProgressStep() != AnnouncementProgressStepEnum.STEP_2_BID_CLOSED)) {
            throw new CustomException(ErrorCode.ANNOUNCEMENT_NOT_EDITABLE);
        }

        if (announcementRepository.existsByClientInfoAndIdNot(announcement.getClientInfo(), announcement.getId())) {
            throw new CustomException(ErrorCode.GUEST_CLIENT_INFO_SHARED);
        }

        boolean complete = ClientInfo.hasRequiredCompanyInfo(
                request.getCompanyName(), request.getBusinessNumber(), request.getAddress());
        if (!complete && bidRepository.countByAnnouncementCodeWithLock(announcementCode) > 0) {
            throw new CustomException(ErrorCode.CLIENT_INFO_REQUIRED_AFTER_BID);
        }

        ClientInfo clientInfo = announcement.getClientInfo();
        applyClientInfoUpdate(clientInfo, request);
        announcement.updateProgressStep(complete
                ? AnnouncementProgressStepEnum.STEP_2_BID_CLOSED
                : AnnouncementProgressStepEnum.STEP_1_REGISTERED);

        clientRepository.flush();
        return ClientInfoResponse.from(clientInfo);
    }

    private void applyClientInfoUpdate(ClientInfo clientInfo, ClientInfoRequest request) {
        clientInfo.update(
                request.getCompanyName(),
                request.getBusinessNumber(),
                request.getRepresentativeName(),
                request.getContact(),
                request.getEmail(),
                request.getAddress(),
                request.getWebsite(),
                request.getDescription()
        );
    }

    public ClientInfoResponse getClientInfo(Long userId) {
        ClientInfo clientInfo = clientRepository.findByUser_Id(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLIENT_INFO_NOT_FOUND));

        return ClientInfoResponse.from(clientInfo);
    }
}
