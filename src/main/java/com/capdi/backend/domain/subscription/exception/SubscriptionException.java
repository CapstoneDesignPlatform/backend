package com.capdi.backend.domain.subscription.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class SubscriptionException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public SubscriptionException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static SubscriptionException conflict(String code, String message) {
        return new SubscriptionException(HttpStatus.CONFLICT, code, message);
    }

    public static SubscriptionException unavailable() {
        return new SubscriptionException(HttpStatus.SERVICE_UNAVAILABLE, "SUBSCRIPTION_PROVIDER_UNAVAILABLE",
                "구독 결제 연동이 설정되지 않았습니다.");
    }
}
