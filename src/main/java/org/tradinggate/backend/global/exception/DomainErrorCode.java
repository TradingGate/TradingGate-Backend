package org.tradinggate.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DomainErrorCode implements ErrorCode {
    INVALID_PARAM(HttpStatus.BAD_REQUEST, "잘못된 요청 파라미터입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다."),
    INVALID_STATE(HttpStatus.CONFLICT, "허용되지 않는 상태 전이입니다."),
    OPERATION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "현재 상태에서 허용되지 않는 작업입니다."),
    CONFLICT(HttpStatus.CONFLICT, "충돌이 발생했습니다."),
    DATA_INTEGRITY(HttpStatus.BAD_REQUEST, "데이터 무결성이 위반되었습니다."),

    DUPLICATE_ANSWER(HttpStatus.CONFLICT, "이미 응답을 제출한 문제입니다."),
    ANSWER_NOT_FOUND(HttpStatus.BAD_REQUEST, "답변 기록을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
