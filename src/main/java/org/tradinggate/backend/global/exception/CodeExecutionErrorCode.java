package org.tradinggate.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CodeExecutionErrorCode implements ErrorCode {
    CODE_SUBMISSION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "코드 제출에 실패했습니다."),
    CODE_RESULT_FETCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "채점 결과 조회에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
