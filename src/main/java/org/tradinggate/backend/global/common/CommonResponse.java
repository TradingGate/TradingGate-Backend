package org.tradinggate.backend.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommonResponse <T> {
    private String message;
    private int statusCode;
    private T result;
}
