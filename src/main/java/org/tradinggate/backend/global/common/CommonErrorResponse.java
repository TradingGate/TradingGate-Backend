package org.tradinggate.backend.global.common;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CommonErrorResponse {
    private String message;
    private String error;
    private int statusCode;
    private LocalDateTime timestamp;
}
