package com.capdi.backend.domain.expert.util;

import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class BusinessInputValidator {
    private final BusinessRegistrationExtractor extractor;

    public String number(String raw) {
        if (raw == null || !raw.matches("[0-9\\h-]+")) throw invalid();
        String value = raw.replaceAll("[\\h-]", "");
        if (!extractor.isValidBusinessNumber(value)) throw invalid();
        return value;
    }

    public String name(String raw) {
        if (raw == null) throw invalid();
        String value = raw.strip();
        if (value.isBlank() || value.length() > 100 || value.codePoints().anyMatch(Character::isISOControl)
                || !value.codePoints().anyMatch(Character::isLetter)) throw invalid();
        return value;
    }

    public LocalDate date(LocalDate date) {
        if (date == null || date.getYear() < 1 || date.getYear() > 9999) throw invalid();
        return date;
    }

    private CustomException invalid() { return new CustomException(ErrorCode.INVALID_INPUT); }
}
