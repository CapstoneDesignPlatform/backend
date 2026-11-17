package com.capdi.backend.domain.subscription.controller;

import com.capdi.backend.domain.subscription.exception.SubscriptionException;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = SubscriptionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SubscriptionExceptionHandler {
    public record ErrorData(String code) { }

    @ExceptionHandler(SubscriptionException.class)
    public ResponseEntity<ApiResponse<ErrorData>> subscription(SubscriptionException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.fail(e.getMessage(), new ErrorData(e.getCode())));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalidBody() {
        return ResponseEntity.badRequest().body(ApiResponse.fail("입력값이 올바르지 않습니다."));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> domain(CustomException e) {
        return ResponseEntity.status(e.getErrorCode().getHttpStatus()).body(ApiResponse.fail(e.getErrorCode().getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("접근 권한이 없습니다."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("인증이 필요합니다."));
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<ErrorData>> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail("구독 처리 상태를 확인하고 같은 요청 키로 재시도해주세요.",
                new ErrorData("SUBSCRIPTION_CONCURRENT_REQUEST")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorData>> unexpected() {
        // JDBC/provider exception messages can contain credentials. Never serialize or log them here.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.fail("구독 처리 결과를 확인할 수 없습니다. 기존 주문을 조회해주세요.",
                new ErrorData("SUBSCRIPTION_PROCESSING_UNAVAILABLE")));
    }
}
