package org.tradinggate.backend.trading.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * [A-1] Trading API - 에러 응답 DTO
 *
 * 역할:
 * - 통일된 에러 응답 형식
 */
@Getter
@Builder
@AllArgsConstructor
public class ApiErrorResponse {

  private String errorCode;
  private String message;
  private LocalDateTime timestamp;
  private String path;

  public static ApiErrorResponse of(String errorCode, String message, String path) {
    return ApiErrorResponse.builder()
        .errorCode(errorCode)
        .message(message)
        .path(path)
        .timestamp(LocalDateTime.now())
        .build();
  }
}
