package org.tradinggate.backend.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommonResponse<T> {
    private String message;
    private int statusCode;
    private T result;

    public static <T> CommonResponse<T> success(T result) {
        return new CommonResponse<>("Success", 200, result);
    }

}
