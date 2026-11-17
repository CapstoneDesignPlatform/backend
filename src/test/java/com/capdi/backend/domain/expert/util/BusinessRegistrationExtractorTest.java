package com.capdi.backend.domain.expert.util;

import com.capdi.backend.domain.expert.dto.BusinessRegistrationExtraction.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class BusinessRegistrationExtractorTest {
    private final BusinessRegistrationExtractor extractor = new BusinessRegistrationExtractor();

    @ParameterizedTest
    @ValueSource(strings = {"1234567891", "123-45-67891", "123 45 67891", "1 2 3 4 5 6 7 8 9 1"})
    void validatesAndNormalizesLabelledBusinessNumber(String input) {
        var field = extractor.extract("사업자등록번호: " + input).getBusinessNumber();
        assertThat(field.getStatus()).isEqualTo(Status.CANDIDATE);
        assertThat(field.getValue()).isEqualTo("1234567891");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567890", "123456789", "12345678911", "123-45-6789O", "0000000000"})
    void refusesMalformedNumberAndChecksum(String input) {
        var field = extractor.extract("등록번호: " + input).getBusinessNumber();
        assertThat(field.getStatus()).isEqualTo(Status.INVALID);
        assertThat(field.getValue()).isNull();
        assertThat(field.getCandidates()).allMatch(candidate -> !candidate.isValid());
    }

    @Test
    void neverJoinsAllNumbersInDocumentOrTreatsCorporateNumberAsBusinessNumber() {
        var result = extractor.extract("등록번호: 123\n다른번호 4567891\n법인등록번호: 1234567891234");
        assertThat(result.getBusinessNumber().getValue()).isNull();
        assertThat(result.getBusinessNumber().getCandidates()).hasSize(1);
        assertThat(extractor.extract("문서번호 1234567891").getBusinessNumber().getStatus()).isEqualTo(Status.MISSING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"대표자: 홍길동", "대 표 자 성 명 : 홍길동", "성 명 (대표자): 홍길동", "대표자명: 홍길동", "대표자 명: 홍길동"})
    void representativeLabels(String text) {
        assertThat(extractor.extract(text).getRepresentativeName().getValue()).isEqualTo("홍길동");
    }

    @ParameterizedTest
    @ValueSource(strings = {"상호: 예시회사", "법 인 명: 예시회사", "상호(법인명): 예시회사", "상호(단체명): 예시회사", "상호명: 예시회사", "상 호 (법 인 명): 예시회사"})
    void companyLabels(String text) {
        assertThat(extractor.extract(text).getCompanyName().getValue()).isEqualTo("예시회사");
    }

    @ParameterizedTest
    @ValueSource(strings = {"상호", "상 호", "상호명", "상호(법인명)", "법인명", "상호(단체명)"})
    void syntheticEnglishCompanyLabels(String label) {
        var field = extractor.extract(label + "： Synthetic Demo Company").getCompanyName();
        assertThat(field.getStatus()).isEqualTo(Status.CANDIDATE);
        assertThat(field.getValue()).isEqualTo("Synthetic Demo Company");
        assertThat(field.getCandidates()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Synthetic  Demo\tCompany", "Synthetic\u00a0Demo\u00a0Company\u00a0",
            "Synthetic Demo Company\u202f", "Synthetic Demo Company\u2007", "Synthetic Demo Company\u3000"})
    void normalizesHorizontalWhitespaceBeforeTrimmingName(String value) {
        var result = extractor.extract("상호: " + value + "\n대표자: " + value);
        assertThat(result.getCompanyName().getStatus()).isEqualTo(Status.CANDIDATE);
        assertThat(result.getCompanyName().getValue()).isEqualTo("Synthetic Demo Company");
        assertThat(result.getRepresentativeName().getValue()).isEqualTo("Synthetic Demo Company");
        // Normalization must not rewrite the original evidence.
        assertThat(result.getCompanyName().getCandidates().get(0).getEvidence()).isEqualTo("상호: " + value);
    }

    @Test
    void labelMayPrecedeValueOnImmediateNextLineButDoesNotJoinWrappedValues() {
        var nextLine = extractor.extract("상호:\r\nSynthetic Demo Company\r\n대표자: Synthetic Owner").getCompanyName();
        assertThat(nextLine.getStatus()).isEqualTo(Status.NEEDS_CONFIRMATION);
        assertThat(nextLine.getValue()).isNull();
        assertThat(nextLine.getCandidates().get(0).getReason()).isEqualTo("UNVERIFIED_NEXT_LINE_ASSOCIATION");
        assertThat(extractor.extract("상호: Synthetic Demo\nCompany\n대표자: Synthetic Owner")
                .getCompanyName().getValue()).isEqualTo("Synthetic Demo");
        assertThat(extractor.extract("상호:\n\nSynthetic Demo Company")
                .getCompanyName().getStatus()).isEqualTo(Status.INVALID);
        assertThat(extractor.extract("상\n호: Synthetic Demo Company")
                .getCompanyName().getStatus()).isEqualTo(Status.MISSING);
    }

    @Test
    void neverSubstitutesRepresentativeOrChoosesOneOfDifferentCompanies() {
        var labelled = extractor.extract("대표자: Synthetic Owner 상호: Synthetic Demo Company 개업일: 2024-02-29");
        assertThat(labelled.getRepresentativeName().getValue()).isEqualTo("Synthetic Owner");
        assertThat(labelled.getCompanyName().getValue()).isEqualTo("Synthetic Demo Company");
        var absent = extractor.extract("상호:\n대표자: Synthetic Owner");
        assertThat(absent.getCompanyName().getStatus()).isEqualTo(Status.INVALID);
        assertThat(absent.getCompanyName().getValue()).isNull();
        var ambiguous = extractor.extract("상호: Synthetic Demo Company\n법인명: Other Synthetic Company");
        assertThat(ambiguous.getCompanyName().getStatus()).isEqualTo(Status.NEEDS_CONFIRMATION);
        assertThat(ambiguous.getCompanyName().getValue()).isNull();
        assertThat(ambiguous.getCompanyName().getCandidates()).hasSize(2);
    }

    @Test
    void doesNotSilentlyCorrectEnglishCasePunctuationOrUnreadableCharacters() {
        assertThat(extractor.extract("상호: SYNTHETIC DEMO COMPANY").getCompanyName().getValue())
                .isEqualTo("SYNTHETIC DEMO COMPANY");
        assertThat(extractor.extract("상호: Synthetic Demo Company.").getCompanyName().getValue())
                .isEqualTo("Synthetic Demo Company.");
        assertThat(extractor.extract("상호: Synthetic Demo Compan?").getCompanyName().getStatus())
                .isEqualTo(Status.INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-02-29", "2024.2.29", "2024/02/29", "2024년 02월 29일", "20240229"})
    void strictOpeningDates(String date) {
        assertThat(extractor.extract("개 업 연 월 일: " + date).getOpeningDate().getValue())
                .isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2023-02-29", "2024-13-01", "2024-04-31", "0000-01-01", "2024-2-2O", ""})
    void invalidDatesNeverNormalizeIntoValidDates(String date) {
        var field = extractor.extract("개업일자: " + date).getOpeningDate();
        assertThat(field.getStatus()).isEqualTo(Status.INVALID);
        assertThat(field.getValue()).isNull();
    }

    @Test
    void ignoresIssueDateAndProvidesEvidenceWithoutInventingPages() {
        var result = extractor.extract("발급일: 2025-12-31\n개업연월일: 2024-02-29\n대표자: 홍길동 상호: 예시회사");
        assertThat(result.getOpeningDate().getValue()).isEqualTo(LocalDate.of(2024, 2, 29));
        var source = result.getOpeningDate().getCandidates().get(0);
        assertThat(source.getPageNumber()).isNull();
        assertThat(source.getLineNumber()).isEqualTo(2);
        assertThat(source.getEvidence()).isEqualTo("개업연월일: 2024-02-29");
        assertThat(result.getRepresentativeName().getValue()).isEqualTo("홍길동");
        assertThat(result.getCompanyName().getValue()).isEqualTo("예시회사");
        assertThat(extractor.extract("발급일: 2024-02-29").getOpeningDate().getStatus()).isEqualTo(Status.MISSING);
    }

    @Test
    void missingAmbiguousAndUnreadableCandidatesRequireConfirmation() {
        var result = extractor.extract("등록번호: 1234567891\n등록번호: 9876543215\n대표자: 홍길동 / 김길동\n상호: ???");
        assertThat(result.getBusinessNumber().getStatus()).isEqualTo(Status.NEEDS_CONFIRMATION);
        assertThat(result.getBusinessNumber().getValue()).isNull();
        assertThat(result.getBusinessNumber().getCandidates()).hasSize(2);
        assertThat(result.getRepresentativeName().getStatus()).isEqualTo(Status.NEEDS_CONFIRMATION);
        assertThat(result.getCompanyName().getStatus()).isEqualTo(Status.INVALID);
        assertThat(result.getOpeningDate().getStatus()).isEqualTo(Status.MISSING);
        assertThat(extractor.extract(null).getBusinessNumber().getStatus()).isEqualTo(Status.MISSING);
        assertThat(extractor.extract("").getRepresentativeName().getStatus()).isEqualTo(Status.MISSING);
    }

    @Test
    void identicalNormalizedCandidatesKeepAllEvidenceAndDistinctInvalidCandidateIsNotHidden() {
        var same = extractor.extract("등록번호: 123-45-67891\n등록번호: 1234567891").getBusinessNumber();
        assertThat(same.getValue()).isEqualTo("1234567891");
        assertThat(same.getCandidates()).hasSize(2);
        var mixed = extractor.extract("등록번호: 1234567891\n등록번호: 123456789O").getBusinessNumber();
        assertThat(mixed.getStatus()).isEqualTo(Status.NEEDS_CONFIRMATION);
        assertThat(mixed.getValue()).isNull();
    }

    @Test
    void supportsImmediateNextLineValueButNeverSkipsToAnotherLabel() {
        assertThat(extractor.extract("대표자\n홍길동\n상호: 예시").getRepresentativeName().getValue()).isEqualTo("홍길동");
        assertThat(extractor.extract("대표자\n상호: 예시").getRepresentativeName().getValue()).isNull();
    }

    @Test
    void neverUsesPartialLabelWordsToConfirmNames() {
        var result = extractor.extract("대표자전화: 01012345678\n상호변경이력: 다른회사");
        assertThat(result.getRepresentativeName().getStatus()).isEqualTo(Status.MISSING);
        assertThat(result.getCompanyName().getStatus()).isEqualTo(Status.MISSING);
    }

    @ParameterizedTest(name = "unverified association case {index}")
    @ValueSource(strings = {
            "상호:\nSynthetic Demo Company\n대표자: Synthetic Owner",
            "상호:\nSynthetic Owner\n대표자: Synthetic Owner\nSynthetic Demo Company",
            "상호:\n2024년 02월 29일\n개업일: 2024-02-29\nSynthetic Demo Company",
            "사업자등록번호\n대표자\n상호\nSynthetic Owner\nSynthetic Demo Company",
            "상호:\nUnrelated Synthetic Heading\n\nSynthetic Demo Company",
            "대표자: Synthetic Owner 상호:\nSynthetic Owner\nSynthetic Demo Company"})
    void unlabelledNextLineCompanyIsEvidenceNotAConfirmedAssociation(String text) {
        var field = extractor.extract(text).getCompanyName();
        assertThat(field.getStatus()).isEqualTo(Status.NEEDS_CONFIRMATION);
        assertThat(field.getValue()).isNull();
        assertThat(field.getCandidates()).hasSize(1);
        var candidate = field.getCandidates().get(0);
        assertThat(candidate.isValid()).isTrue(); // Syntax is readable; field association is unverified.
        assertThat(candidate.getReason()).isEqualTo("UNVERIFIED_NEXT_LINE_ASSOCIATION");
        assertThat(candidate.getEvidence().contains("\n")).isTrue();
        assertThat(candidate.getPageNumber()).isNull();
    }

    @Test
    void neverSearchesDistantCompanyOrConsumesAnotherLabelOrNumericValue() {
        for (String text : new String[]{"상호:\n대표자: Synthetic Owner\nSynthetic Demo Company",
                "상호:\n\nSynthetic Demo Company", "상호:\n1234567891\nSynthetic Demo Company",
                "상호:\n2024-02-29\nSynthetic Demo Company"}) {
            var field = extractor.extract(text).getCompanyName();
            assertThat(field.getStatus()).isEqualTo(Status.INVALID);
            assertThat(field.getValue()).isNull();
        }
        assertThat(extractor.extract("대표자: Synthetic Owner\nSynthetic Demo Company")
                .getCompanyName().getStatus()).isEqualTo(Status.MISSING);
    }

    @Test
    void sameLineAssociationRemainsCandidateButDoesNotResolveUnverifiedDuplicate() {
        var direct = extractor.extract("대표자: Synthetic Owner 상호: Synthetic Demo Company 개업일: 2024-02-29")
                .getCompanyName();
        assertThat(direct.getStatus()).isEqualTo(Status.CANDIDATE);
        assertThat(direct.getCandidates()).hasSize(1);
        var mixed = extractor.extract("상호: Synthetic Demo Company\n법인명:\nSynthetic Demo Company")
                .getCompanyName();
        assertThat(mixed.getStatus()).isEqualTo(Status.NEEDS_CONFIRMATION);
        assertThat(mixed.getValue()).isNull();
        assertThat(mixed.getCandidates()).hasSize(2);
    }
}
