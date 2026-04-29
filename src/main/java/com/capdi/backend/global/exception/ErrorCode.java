package com.capdi.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 회원
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 올바르지 않습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    INVALID_SIGNUP_ROLE(HttpStatus.BAD_REQUEST, "해당 역할로는 회원가입이 불가합니다."),
    EXPERT_NOT_APPROVED(HttpStatus.FORBIDDEN, "승인되지 않은 전문가입니다."),

    // 토큰
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "리프레시 토큰을 찾을 수 없습니다."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 만료되었습니다."),

    // 공고
    ANNOUNCEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 공고입니다."),
    JOB_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 잡포스트입니다."),
    ANNOUNCEMENT_CLOSED(HttpStatus.BAD_REQUEST, "마감된 공고입니다."),
    ANNOUNCEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "공고에 대한 권한이 없습니다."),

    // 입찰
    BID_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 입찰입니다."),
    BID_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 입찰에 참여했습니다."),
    BID_PRICE_TOO_LOW(HttpStatus.BAD_REQUEST, "입찰 가격이 최저가보다 낮습니다."),

    // 계약/결제
    CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 계약입니다."),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "결제에 실패했습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 결제입니다."),

    // 정산
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 정산입니다."),
    SETTLEMENT_ALREADY_DONE(HttpStatus.CONFLICT, "이미 정산이 완료되었습니다."),

    // 평가
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 평가를 작성했습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 평가입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
