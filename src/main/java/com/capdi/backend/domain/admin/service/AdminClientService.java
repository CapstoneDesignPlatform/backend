package com.capdi.backend.domain.admin.service;

import com.capdi.backend.domain.admin.dto.*;
import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import com.capdi.backend.domain.announcement.repository.AnnouncementRepository;
import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.bid.repository.BidRepository;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.client.repository.ClientRepository;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.entity.UserTypeEnum;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminClientService {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final AnnouncementRepository announcementRepository;
    private final BidRepository bidRepository;

    // 의뢰인 전체 목록 조회 (페이징)
    public Page<AdminClientSummaryResponse> getClientList(Pageable pageable) {
        return userRepository.findAllByUserType(UserTypeEnum.CLIENT, pageable)
                .map(user -> {
                    ClientInfo clientInfo = clientRepository.findByUser_Id(user.getId()).orElse(null);
                    return AdminClientSummaryResponse.from(user, clientInfo);
                });
    }

    // 의뢰인 상세 조회
    public AdminClientResponse getClientDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.getUserType() != UserTypeEnum.CLIENT) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        ClientInfo clientInfo = clientRepository.findByUser_Id(userId).orElse(null);
        return AdminClientResponse.from(user, clientInfo);
    }

    // 특정 의뢰인의 공고 목록 조회
    public List<AdminAnnouncementSummaryResponse> getAnnouncementsByClient(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ClientInfo clientInfo = clientRepository.findByUser_Id(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLIENT_INFO_NOT_FOUND));

        return announcementRepository.findAllByClientInfo(clientInfo).stream()
                .map(announcement -> {
                    int bidCount = bidRepository.countByAnnouncement(announcement);
                    return AdminAnnouncementSummaryResponse.from(announcement, bidCount);
                })
                .toList();
    }

    // 전체 공고 목록 조회 (페이징, 상태 필터)
    public Page<AdminAnnouncementSummaryResponse> getAnnouncementList(AnnouncementStatusEnum status, Pageable pageable) {
        Page<Announcement> announcements = (status != null)
                ? announcementRepository.findAllByStatus(status, pageable)
                : announcementRepository.findAll(pageable);

        return announcements.map(announcement -> {
            int bidCount = bidRepository.countByAnnouncement(announcement);
            return AdminAnnouncementSummaryResponse.from(announcement, bidCount);
        });
    }

    // 공고 상세 조회 (입찰 리스트 포함)
    public AdminAnnouncementDetailResponse getAnnouncementDetail(String announcementCode) {
        Announcement announcement = announcementRepository.findByAnnouncementCode(announcementCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        List<Bid> bids = bidRepository.findAllByAnnouncement(announcement);
        return AdminAnnouncementDetailResponse.from(announcement, bids);
    }

    // 공고 상태 변경
    @Transactional
    public void updateAnnouncementStatus(String announcementCode, AnnouncementStatusEnum newStatus) {
        Announcement announcement = announcementRepository.findByAnnouncementCode(announcementCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        announcement.updateStatus(newStatus);
    }
}
