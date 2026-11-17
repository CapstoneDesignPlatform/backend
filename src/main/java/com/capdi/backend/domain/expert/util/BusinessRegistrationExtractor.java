package com.capdi.backend.domain.expert.util;

import com.capdi.backend.domain.expert.dto.BusinessRegistrationExtraction;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationExtraction.Candidate;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationExtraction.Field;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationExtraction.Status;
import org.springframework.stereotype.Component;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Conservative, label-based candidates, never official registration verification. */
@Component
public class BusinessRegistrationExtractor {
    private static final String NUMBER = "사\\h*업\\h*자\\h*등\\h*록\\h*번\\h*호|등\\h*록\\h*번\\h*호";
    private static final String PERSON = "대\\h*표\\h*자(?:\\h*성\\h*명|\\h*명)?|성\\h*명\\h*\\(\\h*대\\h*표\\h*자\\h*\\)";
    private static final String COMPANY = "상\\h*호(?:\\h*명)?(?:\\h*\\(\\h*(?:법\\h*인\\h*명|단\\h*체\\h*명)\\h*\\))?|법\\h*인\\h*명(?:\\h*\\(\\h*단\\h*체\\h*명\\h*\\))?";
    private static final String OPENING = "개\\h*업\\h*(?:연\\h*월\\h*일|일\\h*자|일)";
    private static final String STOP = "법\\h*인\\h*등\\h*록\\h*번\\h*호|발\\h*급\\h*(?:일자|일)|사업장\\h*소재지|주소|업태|종목";
    private static final Pattern LABEL = Pattern.compile("(?<![\\p{L}\\p{N}])(?:(?<number>" + NUMBER
            + ")|(?<person>" + PERSON + ")|(?<company>" + COMPANY + ")|(?<opening>" + OPENING
            + ")|(?<stop>" + STOP + "))(?=\\h|[:：]|$|[0-9])\\h*[:：]?\\h*");
    private static final Pattern DATE = Pattern.compile("^(?:(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})"
            + "|(\\d{4})\\h*년\\h*(\\d{1,2})\\h*월\\h*(\\d{1,2})\\h*일|(\\d{4})(\\d{2})(\\d{2}))$");

    public BusinessRegistrationExtraction extract(String text) {
        List<Source> sources = sources(text == null ? "" : text);
        return BusinessRegistrationExtraction.builder()
                .businessNumber(field(sources, "number", this::number))
                .representativeName(field(sources, "person", this::name))
                .companyName(field(sources, "company", this::name))
                .openingDate(field(sources, "opening", this::date)).build();
    }

    public boolean isValidBusinessNumber(String normalized) {
        if (normalized == null || !normalized.matches("[0-9]{10}") || normalized.equals("0000000000")) return false;
        int[] weights = {1, 3, 7, 1, 3, 7, 1, 3, 5};
        int sum = 0;
        for (int i = 0; i < weights.length; i++) sum += (normalized.charAt(i) - '0') * weights[i];
        sum += ((normalized.charAt(8) - '0') * 5) / 10;
        return (10 - sum % 10) % 10 == normalized.charAt(9) - '0';
    }

    private Parsed<String> number(String raw) {
        String value = raw.replaceAll("[\\h-]", "");
        boolean valid = raw.matches("[0-9\\h-]+") && isValidBusinessNumber(value);
        return new Parsed<>(valid ? value : null, valid ? null : "INVALID_NUMBER_OR_CHECKSUM");
    }

    private Parsed<String> name(String raw) {
        // NBSP/narrow NBSP/figure space are horizontal whitespace, but not stripped by String.strip().
        String value = raw.replaceAll("\\h+", " ").strip();
        boolean valid = !value.isBlank() && value.length() <= 100
                && value.codePoints().anyMatch(Character::isLetter)
                && !value.matches(".*[?？�].*");
        return new Parsed<>(valid ? value : null, valid ? null : "UNREADABLE_VALUE");
    }

    private Parsed<LocalDate> date(String raw) {
        var matcher = DATE.matcher(raw.replaceAll("\\h*([-./])\\h*", "$1"));
        if (!matcher.matches()) return new Parsed<>(null, "INVALID_DATE");
        int offset = matcher.group(1) != null ? 1 : matcher.group(4) != null ? 4 : 7;
        try {
            int year = Integer.parseInt(matcher.group(offset));
            if (year < 1) return new Parsed<>(null, "INVALID_DATE");
            return new Parsed<>(LocalDate.of(year, Integer.parseInt(matcher.group(offset + 1)),
                    Integer.parseInt(matcher.group(offset + 2))), null);
        } catch (DateTimeException e) {
            return new Parsed<>(null, "INVALID_DATE");
        }
    }

    private <T> Field<T> field(List<Source> sources, String kind, Function<String, Parsed<T>> parse) {
        List<Candidate<T>> candidates = sources.stream().filter(source -> source.kind().equals(kind)).map(source -> {
            Parsed<T> parsed = parse.apply(source.raw());
            return Candidate.<T>builder().value(parsed.value()).raw(source.raw()).valid(parsed.reason() == null)
                    .reason(parsed.reason() != null ? parsed.reason()
                            : source.unverifiedAssociation() ? "UNVERIFIED_NEXT_LINE_ASSOCIATION" : null)
                    .label(source.label()).evidence(source.evidence())
                    .lineNumber(source.line()).pageNumber(null).build();
        }).toList();
        List<T> values = candidates.stream().filter(Candidate::isValid).map(Candidate::getValue).distinct().toList();
        boolean invalid = candidates.stream().anyMatch(candidate -> !candidate.isValid());
        boolean unverified = sources.stream().anyMatch(source -> source.kind().equals(kind) && source.unverifiedAssociation());
        Status status = candidates.isEmpty() ? Status.MISSING : values.isEmpty() ? Status.INVALID
                : values.size() != 1 || invalid || unverified ? Status.NEEDS_CONFIRMATION : Status.CANDIDATE;
        return Field.<T>builder().status(status).value(status == Status.CANDIDATE ? values.get(0) : null)
                .candidates(candidates).build();
    }

    private List<Source> sources(String text) {
        String[] lines = text.split("\\R", -1);
        List<Source> result = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            List<Marker> markers = new ArrayList<>();
            var matcher = LABEL.matcher(lines[i]);
            while (matcher.find()) {
                String kind = List.of("number", "person", "company", "opening", "stop").stream()
                        .filter(key -> matcher.group(key) != null).findFirst().orElseThrow();
                markers.add(new Marker(kind, matcher.start(), matcher.end(), matcher.group().strip()));
            }
            for (int m = 0; m < markers.size(); m++) {
                Marker marker = markers.get(m);
                if (marker.kind().equals("stop")) continue;
                String raw = lines[i].substring(marker.end(), m + 1 < markers.size() ? markers.get(m + 1).start() : lines[i].length()).strip();
                String evidence = lines[i];
                boolean unverifiedAssociation = false;
                if (raw.isEmpty() && i + 1 < lines.length && !lines[i + 1].isBlank() && !LABEL.matcher(lines[i + 1]).find()) {
                    raw = lines[i + 1].strip();
                    evidence += "\n" + lines[i + 1];
                    // Text order alone cannot associate an unlabelled line with a company label.
                    // Keep it as evidence, but never select it as the field's confirmed candidate.
                    unverifiedAssociation = marker.kind().equals("company");
                }
                // Explicitly separated alternatives are candidates, never a combined value.
                for (String candidate : raw.split(marker.kind().equals("opening") ? "[;|]" : "[;/|]", -1)) {
                    result.add(new Source(marker.kind(), marker.label(), candidate.strip(), evidence, i + 1, unverifiedAssociation));
                }
            }
        }
        return result;
    }

    private record Parsed<T>(T value, String reason) { }
    private record Marker(String kind, int start, int end, String label) { }
    private record Source(String kind, String label, String raw, String evidence, int line, boolean unverifiedAssociation) { }
}
