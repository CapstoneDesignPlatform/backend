package com.capdi.backend.domain.expert.client;

import com.capdi.backend.domain.expert.config.NtsBusinessProperties;
import com.capdi.backend.domain.expert.dto.NtsBusinessResult;
import com.capdi.backend.domain.expert.dto.NtsBusinessResult.*;
import com.capdi.backend.domain.expert.dto.nts.NtsProviderDtos.*;
import com.capdi.backend.domain.expert.util.BusinessInputValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NtsBusinessClient {
    private final NtsBusinessProperties properties;
    private final NtsHttpTransport transport;
    private final ObjectMapper mapper;
    private final BusinessInputValidator validator;
    private static final String BASE = "https://api.odcloud.kr/api/nts-businessman/v1";
    private static final String NOT_REGISTERED = "국세청에 등록되지 않은 사업자등록번호입니다";

    public NtsBusinessResult validate(String rawNumber, LocalDate openingDate, String representative, String company) {
        String number = validator.number(rawNumber);
        var request = new ValidationRequest(List.of(new Business(number,
                validator.date(openingDate).format(DateTimeFormatter.BASIC_ISO_DATE), validator.name(representative),
                company == null || company.isBlank() ? null : validator.name(company))));
        return call("/validate", request, body -> {
            ValidationResponse response = mapper.readerFor(ValidationResponse.class)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(body);
            if (response == null) return invalid();
            if (!"OK".equals(response.code())) return NtsBusinessResult.unavailable(Failure.PROVIDER_ERROR);
            if (!Integer.valueOf(1).equals(response.requests())
                    || response.data() == null || response.data().size() != 1) return invalid();
            var item = response.data().get(0);
            if (item == null || !number.equals(item.number())) return invalid();
            if (item.status() != null && !number.equals(item.status().number())) return invalid();
            if ("02".equals(item.valid()) && Integer.valueOf(0).equals(response.validCount())) {
                return new NtsBusinessResult(Authenticity.NOT_CONFIRMED, BusinessState.UNKNOWN, Failure.NONE);
            }
            if (!"01".equals(item.valid()) || !Integer.valueOf(1).equals(response.validCount())) return invalid();
            BusinessState state = state(item.status(), number);
            if (state == BusinessState.UNKNOWN || state == BusinessState.NOT_FOUND_OR_DELETED) return invalid();
            return new NtsBusinessResult(Authenticity.MATCH, state, Failure.NONE);
        });
    }

    public NtsBusinessResult lookupStatus(String rawNumber) {
        String number = validator.number(rawNumber);
        return call("/status", new StatusRequest(List.of(number)), body -> {
            StatusResponse response = mapper.readerFor(StatusResponse.class)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(body);
            if (response == null) return invalid();
            if (!"OK".equals(response.code())) return NtsBusinessResult.unavailable(Failure.PROVIDER_ERROR);
            if (!Integer.valueOf(1).equals(response.requests())
                    || response.data() == null || response.data().size() != 1) return invalid();
            BusinessState state = state(response.data().get(0), number);
            if (state == BusinessState.UNKNOWN || response.matches() == null || response.matches() < 0 || response.matches() > 1
                    || (state != BusinessState.NOT_FOUND_OR_DELETED && response.matches() != 1)) return invalid();
            return new NtsBusinessResult(Authenticity.NOT_REQUESTED, state, Failure.NONE);
        });
    }

    private BusinessState state(BusinessStatus status, String number) {
        if (status == null || !number.equals(status.number())) return BusinessState.UNKNOWN;
        String message = status.taxType() == null ? "" : status.taxType().strip().replaceFirst("\\.$", "");
        if (NOT_REGISTERED.equals(message)) {
            return status.state() == null || status.state().isBlank() ? BusinessState.NOT_FOUND_OR_DELETED : BusinessState.UNKNOWN;
        }
        return switch (status.state() == null ? "" : status.state()) {
            case "01" -> BusinessState.ACTIVE;
            case "02" -> BusinessState.SUSPENDED;
            case "03" -> BusinessState.CLOSED;
            default -> BusinessState.UNKNOWN;
        };
    }

    private NtsBusinessResult call(String path, Object request, Decoder decoder) {
        if (!properties.isEnabled()) return NtsBusinessResult.unavailable(Failure.DISABLED);
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) return NtsBusinessResult.unavailable(Failure.CONFIGURATION);
        try {
            // Configuration accepts the raw (decoded) key. Encode exactly once, never log the URI.
            URI uri = URI.create(BASE + path + "?serviceKey=" + URLEncoder.encode(properties.getApiKey(), StandardCharsets.UTF_8)
                    + "&returnType=JSON");
            var response = transport.post(uri, mapper.writeValueAsString(request), properties.getResponseTimeout());
            if (response.status() != 200) return NtsBusinessResult.unavailable(switch (response.status()) {
                case 401, 403 -> Failure.AUTHENTICATION;
                case 429 -> Failure.RATE_LIMIT;
                case 400, 404, 411, 413 -> Failure.REQUEST_REJECTED;
                default -> Failure.PROVIDER_ERROR;
            });
            return decoder.decode(response.body());
        } catch (HttpTimeoutException e) {
            return NtsBusinessResult.unavailable(Failure.TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NtsBusinessResult.unavailable(Failure.INTERRUPTED);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            return invalid();
        } catch (java.io.IOException e) {
            return NtsBusinessResult.unavailable(Failure.NETWORK);
        } catch (Exception e) {
            return invalid();
        }
    }

    private NtsBusinessResult invalid() { return NtsBusinessResult.unavailable(Failure.INVALID_RESPONSE); }
    private interface Decoder { NtsBusinessResult decode(String body) throws Exception; }
}
