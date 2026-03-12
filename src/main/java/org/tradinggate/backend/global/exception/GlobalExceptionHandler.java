package org.tradinggate.backend.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.tradinggate.backend.global.common.CommonErrorResponse;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<CommonErrorResponse> handleCustomException(final CustomException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(
                CommonErrorResponse.builder()
                        .message(ex.getMessage())
                        .error(ex.getStatusCode().getReasonPhrase())
                        .statusCode(ex.getStatusCode().value())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(CommonErrorResponse.builder()
                        .message(ex.getBindingResult().getFieldError().getDefaultMessage())
                        .error(HttpStatus.valueOf(ex.getStatusCode().value()).getReasonPhrase())
                        .statusCode(ex.getStatusCode().value())
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}
