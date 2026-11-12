package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.dto.ExpertSearchCategory;
import com.capdi.backend.domain.expert.entity.CertificateTypeCodeEnum;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.FileTypeEnum;
import com.capdi.backend.domain.expert.entity.FileVerificationStatusEnum;
import com.capdi.backend.domain.expert.entity.VerificationStatusEnum;
import com.capdi.backend.domain.user.entity.UserTypeEnum;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ExpertSearchRepository {

    public static final int PAGE_SIZE = 20;

    private static final String PUBLIC_PROFILE =
            "p.verificationStatus = :approved and u.userType = :expertRole";
    private static final String VALID_CERTIFICATE = """
            f.expertProfile = c.expertProfile
            and f.fileType = :certificateFileType
            and f.verificationStatus = :approvedFile
            and c.expiryDate >= :today
            """;
    private static final String LATEST_BUSINESS_INFO = """
            b.id = (select max(latest.id) from BusinessRegistrationInfo latest
                    where latest.expertProfile = b.expertProfile)
            """;

    private final EntityManager entityManager;

    public Page<ExpertProfile> search(
            ExpertSearchCategory category, String keyword, int page, LocalDate today
    ) {
        SearchCondition condition = searchCondition(category, keyword, today);
        String where = " where " + condition.predicate();

        long total = bind(entityManager.createQuery(
                "select count(p) from ExpertProfile p join p.user u" + where, Long.class), condition.parameters())
                .getSingleResult();
        var pageable = PageRequest.of(page - 1, PAGE_SIZE);
        if (pageable.getOffset() >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        List<ExpertProfile> profiles = bind(entityManager.createQuery(
                "select p from ExpertProfile p join fetch p.user u" + where
                        + " order by case when p.businessName is null then 1 else 0 end,"
                        + " p.businessName asc, u.id asc", ExpertProfile.class), condition.parameters())
                .setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(PAGE_SIZE)
                .getResultList();
        return new PageImpl<>(profiles, pageable, total);
    }

    public Optional<ExpertProfile> findPublicProfile(Long userId) {
        return bind(entityManager.createQuery(
                "select p from ExpertProfile p join fetch p.user u where " + PUBLIC_PROFILE
                        + " and u.id = :userId", ExpertProfile.class), publicProfileParameters())
                .setParameter("userId", userId)
                .getResultList().stream().findFirst();
    }

    public Map<Long, String> findRepresentativeNames(Collection<Long> profileIds) {
        if (profileIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery("""
                        select new com.capdi.backend.domain.expert.repository.ExpertRepresentativeRow(
                            b.expertProfile.id, b.representativeName)
                        from BusinessRegistrationInfo b
                        where b.expertProfile.id in :profileIds and
                        """ + LATEST_BUSINESS_INFO, ExpertRepresentativeRow.class)
                .setParameter("profileIds", profileIds)
                .getResultList().stream()
                .collect(Collectors.toMap(ExpertRepresentativeRow::profileId,
                        ExpertRepresentativeRow::representativeName));
    }

    public List<ExpertCertificateTypeRow> findValidCertificateTypes(Collection<Long> profileIds, LocalDate today) {
        if (profileIds.isEmpty()) {
            return List.of();
        }
        return bind(entityManager.createQuery("""
                        select distinct new com.capdi.backend.domain.expert.repository.ExpertCertificateTypeRow(
                            c.expertProfile.id, c.certificateTypeCode)
                        from ExpertCertificate c join c.file f
                        where c.expertProfile.id in :profileIds and
                        """ + VALID_CERTIFICATE, ExpertCertificateTypeRow.class), certificateParameters(today))
                .setParameter("profileIds", profileIds)
                .getResultList();
    }

    private SearchCondition searchCondition(ExpertSearchCategory category, String keyword, LocalDate today) {
        if (keyword.isEmpty()) {
            return new SearchCondition(PUBLIC_PROFILE, publicProfileParameters());
        }
        SearchCondition search = keywordCondition(category, keyword.toLowerCase(Locale.ROOT), today);
        Map<String, Object> parameters = new HashMap<>(publicProfileParameters());
        parameters.putAll(search.parameters());
        return new SearchCondition(PUBLIC_PROFILE + " and (" + search.predicate() + ")", parameters);
    }

    private SearchCondition keywordCondition(ExpertSearchCategory category, String keyword, LocalDate today) {
        String company = "lower(p.businessName) like :keyword escape '!'";
        String representative = "exists (select b.id from BusinessRegistrationInfo b"
                + " where b.expertProfile = p and " + LATEST_BUSINESS_INFO
                + " and lower(b.representativeName) like :keyword escape '!')";
        Map<String, Object> keywordParameters = Map.of("keyword", "%" + escapeLike(keyword) + "%");
        return switch (category) {
            case COMPANY_NAME -> new SearchCondition(company, keywordParameters);
            case REPRESENTATIVE_NAME -> new SearchCondition(representative, keywordParameters);
            case CERTIFICATE -> certificateCondition(keyword, today);
            case ALL -> {
                SearchCondition certificate = certificateCondition(keyword, today);
                Map<String, Object> parameters = new HashMap<>(keywordParameters);
                parameters.putAll(certificate.parameters());
                yield new SearchCondition(company + " or " + representative + " or " + certificate.predicate(),
                        parameters);
            }
        };
    }

    private SearchCondition certificateCondition(String keyword, LocalDate today) {
        List<CertificateTypeCodeEnum> types = Arrays.stream(CertificateTypeCodeEnum.values())
                .filter(type -> type.getLabel().toLowerCase(Locale.ROOT).contains(keyword))
                .toList();
        if (types.isEmpty()) {
            return new SearchCondition("1 = 0", Map.of());
        }
        Map<String, Object> parameters = new HashMap<>(certificateParameters(today));
        parameters.put("certificateTypes", types);
        return new SearchCondition("exists (select c.id from ExpertCertificate c join c.file f"
                + " where c.expertProfile = p and " + VALID_CERTIFICATE
                + " and c.certificateTypeCode in :certificateTypes)", parameters);
    }

    private Map<String, Object> publicProfileParameters() {
        return Map.of("approved", VerificationStatusEnum.APPROVED, "expertRole", UserTypeEnum.EXPERT);
    }

    private Map<String, Object> certificateParameters(LocalDate today) {
        return Map.of("certificateFileType", FileTypeEnum.CERTIFICATE,
                "approvedFile", FileVerificationStatusEnum.APPROVED, "today", today);
    }

    private String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private <T> TypedQuery<T> bind(TypedQuery<T> query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
        return query;
    }

    private record SearchCondition(String predicate, Map<String, Object> parameters) {

        private SearchCondition {
            parameters = Map.copyOf(parameters);
        }
    }
}
