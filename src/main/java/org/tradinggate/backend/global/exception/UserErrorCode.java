package org.tradinggate.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode{
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "유저가 존재하지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "해당 작업에 대해서 권한이 존재하지 않습니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "중복 요청이 감지되었습니다.");

    private final HttpStatus status;
    private final String message;
}
