package com.capdi.backend.domain.admin.service;

import com.capdi.backend.domain.admin.dto.excel.AdminAnnouncementExcelRow;
import com.capdi.backend.domain.admin.dto.excel.AdminClientExcelRow;
import com.capdi.backend.domain.admin.dto.excel.AdminExpertExcelRow;
import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo;
import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.VerificationStatusEnum;
import com.capdi.backend.domain.jobpost.entity.JobPost;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.entity.UserTypeEnum;
import com.capdi.backend.global.excel.ExcelFileGenerator;
import com.capdi.backend.global.excel.ExcelFileGenerator.Section;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminExcelService {

    private final EntityManager entityManager;
    private final ExcelFileGenerator excelFileGenerator;

    // 의뢰현황 목록 Excel 파일 생성
    public byte[] createAnnouncementExcel(AnnouncementStatusEnum status, String keyword) {
        List<AdminAnnouncementExcelRow> data = findAnnouncementRows(status, keyword);
        List<String> headers = List.of("의뢰코드", "공고명", "업종", "기업명", "의뢰인", "구분", "상태", "입찰수", "등록일");
        List<List<Object>> rows = data.stream()
                .map(row -> excelRow(
                        row.getAnnouncementCode(),
                        row.getTitle(),
                        row.getIndustry(),
                        row.getCompanyName(),
                        row.getClientName(),
                        row.getClientType(),
                        row.getStatus(),
                        row.getBidCount(),
                        row.getCreatedAt()
                ))
                .toList();

        return excelFileGenerator.generate("의뢰현황", headers, rows);
    }

    // 의뢰 상세 Excel 파일 생성
    public byte[] createAnnouncementDetailExcel(String announcementCode) {
        Announcement announcement = findAnnouncementDetail(announcementCode);
        JobPost jobPost = findJobPost(announcementCode);
        List<Bid> bids = findBids(announcement);
        ClientInfo clientInfo = announcement.getClientInfo();

        List<Section> sections = List.of(
                new Section("의뢰 기본 정보", keyValueHeaders(), List.of(
                        keyValue("의뢰코드", announcement.getAnnouncementCode()),
                        keyValue("의뢰명", jobPost == null ? null : jobPost.getTitle()),
                        keyValue("목적", enumName(announcement.getPurpose())),
                        keyValue("업종", enumName(announcement.getIndustry())),
                        keyValue("상태", enumName(announcement.getStatus())),
                        keyValue("구분", announcement.getUser() == null ? "비회원" : "회원"),
                        keyValue("등록일", announcement.getCreatedAt()),
                        keyValue("최종 수정일", announcement.getUpdatedAt())
                )),
                new Section("의뢰 내용", keyValueHeaders(), List.of(
                        keyValue("필요 면허", announcement.getRequiredLicense()),
                        keyValue("현재 보유 면허", announcement.getCurrentLicense()),
                        keyValue("현재 업종", enumName(announcement.getCurrentIndustry())),
                        keyValue("현재 업종 상세", announcement.getCurrentIndustryDetail()),
                        keyValue("진단 사유", enumName(announcement.getDiagnosisReason())),
                        keyValue("진단 사유 상세", announcement.getDiagnosisReasonDetail()),
                        keyValue("자본금", announcement.getCapital()),
                        keyValue("자본규모", announcement.getCapitalScale()),
                        keyValue("공고 설명", jobPost == null ? null : jobPost.getDescription())
                )),
                new Section("기업 정보", keyValueHeaders(), List.of(
                        keyValue("기업명", clientInfo.getCompanyName()),
                        keyValue("사업자등록번호", clientInfo.getBusinessNumber()),
                        keyValue("대표자명", clientInfo.getRepresentativeName()),
                        keyValue("대표 전화", clientInfo.getContact()),
                        keyValue("이메일", clientInfo.getEmail()),
                        keyValue("주소", clientInfo.getAddress()),
                        keyValue("자본금", clientInfo.getCapital()),
                        keyValue("설립일", clientInfo.getFoundedDate())
                )),
                new Section("입찰 통계", keyValueHeaders(), List.of(
                        keyValue("입찰 전문가 수", bids.size()),
                        keyValue("최저 입찰가", minBidAmount(bids)),
                        keyValue("최고 입찰가", maxBidAmount(bids)),
                        keyValue("평균 입찰가", averageBidAmount(bids))
                )),
                new Section("입찰 전문가 목록", List.of("전문가명", "연락처", "이메일", "입찰가", "상태", "입찰일시"), bidRows(bids))
        );

        return excelFileGenerator.generateSections("의뢰상세", sections);
    }

    // 전문가 관리 목록 Excel 파일 생성
    public byte[] createExpertExcel(VerificationStatusEnum status, String keyword) {
        List<AdminExpertExcelRow> data = findExpertRows(status, keyword);
        List<String> headers = List.of("이름", "이메일", "연락처", "전문분야", "신청일", "승인상태", "승인일", "서류제출여부");
        List<List<Object>> rows = data.stream()
                .map(row -> excelRow(
                        row.getName(),
                        row.getEmail(),
                        row.getPhone(),
                        row.getSpecialty(),
                        row.getAppliedAt(),
                        row.getVerificationStatus(),
                        row.getVerifiedAt(),
                        row.getDocumentSubmitted()
                ))
                .toList();

        return excelFileGenerator.generate("전문가관리", headers, rows);
    }

    // 전문가 상세 Excel 파일 생성
    public byte[] createExpertDetailExcel(Long userId) {
        ExpertProfile expertProfile = findExpertDetail(userId);
        User user = expertProfile.getUser();
        List<BusinessRegistrationInfo> businessInfos = findBusinessInfos(expertProfile.getId());
        List<Object[]> certificates = findCertificateRows(expertProfile.getId());
        List<ExpertFile> files = findExpertFiles(expertProfile.getId());

        List<Section> sections = List.of(
                new Section("전문가 기본 정보", keyValueHeaders(), List.of(
                        keyValue("이름", user.getName()),
                        keyValue("이메일", user.getEmail()),
                        keyValue("연락처", user.getPhone()),
                        keyValue("전문분야", expertProfile.getSpecialty()),
                        keyValue("회사명", expertProfile.getBusinessName()),
                        keyValue("경력", expertProfile.getExperienceYears()),
                        keyValue("승인상태", enumName(expertProfile.getVerificationStatus())),
                        keyValue("신청일", expertProfile.getCreatedAt()),
                        keyValue("승인일", expertProfile.getVerifiedAt()),
                        keyValue("자기소개", expertProfile.getPortfolioDescription())
                )),
                new Section("사업자등록 정보", List.of("상호", "대표자명", "사업자번호", "파일명", "등록일"),
                        businessInfoRows(businessInfos)),
                new Section("자격증 정보", List.of("소유자", "면허 종류", "면허 번호", "발급일", "만료일", "파일명"),
                        certificateRows(certificates)),
                new Section("첨부 서류", List.of("파일명", "종류", "크기", "MIME 타입", "검증상태", "업로드일시"),
                        fileRows(files))
        );

        return excelFileGenerator.generateSections("전문가상세", sections);
    }

    // 의뢰인 관리 목록 Excel 파일 생성
    public byte[] createClientExcel(String keyword) {
        List<AdminClientExcelRow> data = findClientRows(keyword);
        List<String> headers = List.of("이름", "업체명", "사업자번호", "연락처", "이메일", "주소", "가입일");
        List<List<Object>> rows = data.stream()
                .map(row -> excelRow(
                        row.getName(),
                        row.getCompanyName(),
                        row.getBusinessNumber(),
                        row.getContact(),
                        row.getEmail(),
                        row.getAddress(),
                        row.getCreatedAt()
                ))
                .toList();

        return excelFileGenerator.generate("의뢰인관리", headers, rows);
    }

    // 의뢰현황 화면에 필요한 의뢰, 의뢰인, 입찰수 정보를 조합한다.
    private List<AdminAnnouncementExcelRow> findAnnouncementRows(AnnouncementStatusEnum status, String keyword) {
        StringBuilder jpql = new StringBuilder("""
                select a
                from Announcement a
                join fetch a.clientInfo ci
                left join fetch a.user u
                where 1 = 1
                """);

        if (status != null) {
            jpql.append(" and a.status = :status");
        }

        if (hasText(keyword)) {
            jpql.append("""
                     and (
                        lower(a.announcementCode) like :keyword
                        or lower(ci.companyName) like :keyword
                        or lower(ci.representativeName) like :keyword
                     )
                    """);
        }

        jpql.append(" order by a.createdAt desc");

        TypedQuery<Announcement> query = entityManager.createQuery(jpql.toString(), Announcement.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (hasText(keyword)) {
            query.setParameter("keyword", likeKeyword(keyword));
        }

        return query.getResultList().stream()
                .map(announcement -> AdminAnnouncementExcelRow.builder()
                        .announcementCode(announcement.getAnnouncementCode())
                        .title(findJobPostTitle(announcement.getAnnouncementCode()))
                        .industry(enumName(announcement.getIndustry()))
                        .companyName(announcement.getClientInfo().getCompanyName())
                        .clientName(announcement.getClientInfo().getRepresentativeName())
                        .clientType(announcement.getUser() == null ? "비회원" : "회원")
                        .status(enumName(announcement.getStatus()))
                        .bidCount(countBids(announcement))
                        .createdAt(announcement.getCreatedAt())
                        .build())
                .toList();
    }

    // 전문가 기본 정보와 승인/서류 제출 상태를 조합한다.
    private List<AdminExpertExcelRow> findExpertRows(VerificationStatusEnum status, String keyword) {
        StringBuilder jpql = new StringBuilder("""
                select ep
                from ExpertProfile ep
                join fetch ep.user u
                where 1 = 1
                """);

        if (status != null) {
            jpql.append(" and ep.verificationStatus = :status");
        }

        if (hasText(keyword)) {
            jpql.append("""
                     and (
                        lower(u.name) like :keyword
                        or lower(u.email) like :keyword
                        or lower(u.phone) like :keyword
                        or lower(ep.specialty) like :keyword
                     )
                    """);
        }

        jpql.append(" order by ep.createdAt desc");

        TypedQuery<ExpertProfile> query = entityManager.createQuery(jpql.toString(), ExpertProfile.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (hasText(keyword)) {
            query.setParameter("keyword", likeKeyword(keyword));
        }

        return query.getResultList().stream()
                .map(expertProfile -> AdminExpertExcelRow.builder()
                        .name(expertProfile.getUser().getName())
                        .email(expertProfile.getUser().getEmail())
                        .phone(expertProfile.getUser().getPhone())
                        .specialty(expertProfile.getSpecialty())
                        .appliedAt(expertProfile.getCreatedAt())
                        .verificationStatus(enumName(expertProfile.getVerificationStatus()))
                        .verifiedAt(expertProfile.getVerifiedAt())
                        .documentSubmitted(hasExpertDocument(expertProfile.getId()) ? "Y" : "N")
                        .build())
                .toList();
    }

    // 의뢰인 사용자 정보와 사업자 정보를 조합한다.
    private List<AdminClientExcelRow> findClientRows(String keyword) {
        List<User> clients = findClientUsers(keyword);

        return clients.stream()
                .map(user -> {
                    ClientInfo clientInfo = findClientInfo(user.getId());

                    return AdminClientExcelRow.builder()
                            .name(user.getName())
                            .companyName(clientInfo == null ? null : clientInfo.getCompanyName())
                            .businessNumber(clientInfo == null ? null : clientInfo.getBusinessNumber())
                            .contact(clientInfo == null ? user.getPhone() : clientInfo.getContact())
                            .email(clientInfo == null ? user.getEmail() : clientInfo.getEmail())
                            .address(clientInfo == null ? null : clientInfo.getAddress())
                            .createdAt(user.getCreatedAt())
                            .build();
                })
                .toList();
    }

    private Announcement findAnnouncementDetail(String announcementCode) {
        List<Announcement> announcements = entityManager.createQuery("""
                        select a
                        from Announcement a
                        join fetch a.clientInfo ci
                        left join fetch a.user u
                        where a.announcementCode = :announcementCode
                        """, Announcement.class)
                .setParameter("announcementCode", announcementCode)
                .getResultList();

        if (announcements.isEmpty()) {
            throw new EntityNotFoundException("Announcement not found: " + announcementCode);
        }

        return announcements.get(0);
    }

    private ExpertProfile findExpertDetail(Long userId) {
        List<ExpertProfile> experts = entityManager.createQuery("""
                        select ep
                        from ExpertProfile ep
                        join fetch ep.user u
                        where u.id = :userId
                        """, ExpertProfile.class)
                .setParameter("userId", userId)
                .getResultList();

        if (experts.isEmpty()) {
            throw new EntityNotFoundException("Expert not found: " + userId);
        }

        return experts.get(0);
    }

    // 의뢰인 목록 조회 조건에 맞는 CLIENT 사용자만 조회한다.
    private List<User> findClientUsers(String keyword) {
        StringBuilder jpql = new StringBuilder("""
                select distinct u
                from User u
                left join ClientInfo ci on ci.user = u
                where u.userType = :userType
                """);

        if (hasText(keyword)) {
            jpql.append("""
                     and (
                        lower(u.name) like :keyword
                        or lower(u.email) like :keyword
                        or lower(u.phone) like :keyword
                        or lower(ci.companyName) like :keyword
                        or lower(ci.businessNumber) like :keyword
                     )
                    """);
        }

        jpql.append(" order by u.createdAt desc");

        TypedQuery<User> query = entityManager.createQuery(jpql.toString(), User.class);
        query.setParameter("userType", UserTypeEnum.CLIENT);
        if (hasText(keyword)) {
            query.setParameter("keyword", likeKeyword(keyword));
        }

        return query.getResultList();
    }

    // 의뢰코드에 연결된 공고명을 조회한다.
    private String findJobPostTitle(String announcementCode) {
        JobPost jobPost = findJobPost(announcementCode);
        return jobPost == null ? null : jobPost.getTitle();
    }

    private JobPost findJobPost(String announcementCode) {
        List<JobPost> jobPosts = entityManager.createQuery("""
                        select jp
                        from JobPost jp
                        where jp.announcement.announcementCode = :announcementCode
                        order by jp.id desc
                        """, JobPost.class)
                .setParameter("announcementCode", announcementCode)
                .setMaxResults(1)
                .getResultList();

        return jobPosts.isEmpty() ? null : jobPosts.get(0);
    }

    // 의뢰별 입찰 수를 계산한다.
    private long countBids(Announcement announcement) {
        return entityManager.createQuery("""
                        select count(b)
                        from Bid b
                        where b.announcement = :announcement
                        """, Long.class)
                .setParameter("announcement", announcement)
                .getSingleResult();
    }

    private List<Bid> findBids(Announcement announcement) {
        return entityManager.createQuery("""
                        select b
                        from Bid b
                        join fetch b.expertUser u
                        where b.announcement = :announcement
                        order by b.bidAmount asc
                        """, Bid.class)
                .setParameter("announcement", announcement)
                .getResultList();
    }

    // 자격증 또는 사업자등록정보가 있으면 서류 제출로 판단한다.
    private boolean hasExpertDocument(Long expertProfileId) {
        long certificateCount = entityManager.createQuery("""
                        select count(ec)
                        from ExpertCertificate ec
                        where ec.expertProfile.id = :expertProfileId
                        """, Long.class)
                .setParameter("expertProfileId", expertProfileId)
                .getSingleResult();

        long businessInfoCount = entityManager.createQuery("""
                        select count(bi)
                        from BusinessRegistrationInfo bi
                        where bi.expertProfile.id = :expertProfileId
                        """, Long.class)
                .setParameter("expertProfileId", expertProfileId)
                .getSingleResult();

        return certificateCount > 0 || businessInfoCount > 0;
    }

    // 현재 DB에는 expiry_date 컬럼이 없어 ExpertCertificate 엔티티 대신 필요한 컬럼만 조회한다.
    private List<Object[]> findCertificateRows(Long expertProfileId) {
        return entityManager.createNativeQuery("""
                        select
                            ec.owner_name,
                            ec.certificate_name,
                            ec.certificate_number,
                            ec.issue_date,
                            ec.expired_at,
                            f.original_name
                        from expert_certificates ec
                        left join files f on f.id = ec.file_id
                        where ec.expert_profile_id = :expertProfileId
                        order by ec.created_at desc
                        """)
                .setParameter("expertProfileId", expertProfileId)
                .getResultList();
    }

    private List<BusinessRegistrationInfo> findBusinessInfos(Long expertProfileId) {
        return entityManager.createQuery("""
                        select bi
                        from BusinessRegistrationInfo bi
                        left join fetch bi.file f
                        where bi.expertProfile.id = :expertProfileId
                        order by bi.createdAt desc
                        """, BusinessRegistrationInfo.class)
                .setParameter("expertProfileId", expertProfileId)
                .getResultList();
    }

    private List<ExpertFile> findExpertFiles(Long expertProfileId) {
        return entityManager.createQuery("""
                        select f
                        from ExpertFile f
                        where f.expertProfile.id = :expertProfileId
                        order by f.createdAt desc
                        """, ExpertFile.class)
                .setParameter("expertProfileId", expertProfileId)
                .getResultList();
    }

    // 사용자 ID에 연결된 의뢰인 상세 정보를 조회한다.
    private ClientInfo findClientInfo(Long userId) {
        List<ClientInfo> clientInfos = entityManager.createQuery("""
                        select ci
                        from ClientInfo ci
                        where ci.user.id = :userId
                        """, ClientInfo.class)
                .setParameter("userId", userId)
                .setMaxResults(1)
                .getResultList();

        return clientInfos.isEmpty() ? null : clientInfos.get(0);
    }

    private List<List<Object>> bidRows(List<Bid> bids) {
        return bids.stream()
                .map(bid -> excelRow(
                        bid.getExpertUser().getName(),
                        bid.getExpertUser().getPhone(),
                        bid.getExpertUser().getEmail(),
                        bid.getBidAmount(),
                        enumName(bid.getStatus()),
                        bid.getSubmittedAt()
                ))
                .toList();
    }

    private List<List<Object>> businessInfoRows(List<BusinessRegistrationInfo> businessInfos) {
        return businessInfos.stream()
                .map(info -> excelRow(
                        info.getCompanyName(),
                        info.getRepresentativeName(),
                        info.getBusinessNumber(),
                        info.getFile() == null ? null : info.getFile().getOriginalName(),
                        info.getCreatedAt()
                ))
                .toList();
    }

    private List<List<Object>> certificateRows(List<Object[]> certificates) {
        return certificates.stream()
                .map(certificate -> excelRow(
                        certificate[0],
                        certificate[1],
                        certificate[2],
                        certificate[3],
                        certificate[4],
                        certificate[5]
                ))
                .toList();
    }

    private List<List<Object>> fileRows(List<ExpertFile> files) {
        return files.stream()
                .map(file -> excelRow(
                        file.getOriginalName(),
                        enumName(file.getFileType()),
                        file.getFileSize(),
                        enumName(file.getMimeType()),
                        enumName(file.getVerificationStatus()),
                        file.getCreatedAt()
                ))
                .toList();
    }

    private List<String> keyValueHeaders() {
        return List.of("항목", "내용");
    }

    private List<Object> keyValue(String key, Object value) {
        return excelRow(key, value);
    }

    private BigDecimal minBidAmount(List<Bid> bids) {
        return bids.stream()
                .map(Bid::getBidAmount)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private BigDecimal maxBidAmount(List<Bid> bids) {
        return bids.stream()
                .map(Bid::getBidAmount)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    private BigDecimal averageBidAmount(List<Bid> bids) {
        if (bids.isEmpty()) {
            return null;
        }

        BigDecimal total = bids.stream()
                .map(Bid::getBidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.divide(BigDecimal.valueOf(bids.size()), 0, RoundingMode.HALF_UP);
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String likeKeyword(String keyword) {
        return "%" + keyword.toLowerCase() + "%";
    }

    private List<Object> excelRow(Object... values) {
        return Arrays.asList(values);
    }
}
