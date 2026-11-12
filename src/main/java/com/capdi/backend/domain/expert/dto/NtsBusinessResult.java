package com.capdi.backend.domain.expert.dto;

/** Internal interpretation only; contains no provider text, number, name, or credentials. */
public record NtsBusinessResult(Authenticity authenticity, BusinessState businessState, Failure failure) {
    public enum Authenticity { MATCH, NOT_CONFIRMED, NOT_REQUESTED, UNAVAILABLE }
    public enum BusinessState { ACTIVE, SUSPENDED, CLOSED, NOT_FOUND_OR_DELETED, UNKNOWN }
    public enum Failure { NONE, DISABLED, CONFIGURATION, TIMEOUT, NETWORK, INTERRUPTED, AUTHENTICATION,
        RATE_LIMIT, REQUEST_REJECTED, PROVIDER_ERROR, INVALID_RESPONSE, EXECUTION_EXPIRED, RESULT_STORAGE_FAILED, INTERNAL_ERROR }
    public static NtsBusinessResult unavailable(Failure reason) {
        return new NtsBusinessResult(Authenticity.UNAVAILABLE, BusinessState.UNKNOWN, reason);
    }
}
