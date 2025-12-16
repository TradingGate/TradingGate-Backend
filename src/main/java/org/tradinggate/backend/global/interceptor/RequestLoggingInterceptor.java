package org.tradinggate.backend.global.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * HTTP 요청/응답 로깅 인터셉터
 * - 요청 ID 생성 및 로깅
 * - 처리 시간 측정
 */
@Slf4j
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID = "requestId";
    private static final String START_TIME = "startTime";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long startTime = System.currentTimeMillis();

        request.setAttribute(REQUEST_ID, requestId);
        request.setAttribute(START_TIME, startTime);

        log.info("[{}] -> {} {}", requestId, request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler, @Nullable Exception ex) {
        String requestId = (String) request.getAttribute(REQUEST_ID);
        Long startTime = (Long) request.getAttribute(START_TIME);
        long duration = System.currentTimeMillis() - (startTime != null ? startTime : System.currentTimeMillis());

        if (ex != null) {
            log.error("[{}] <- {} {} ({}ms) Error: {}", requestId, request.getMethod(), request.getRequestURI(),
                    duration, ex.getMessage());
        } else {
            log.info("[{}] <- {} {} ({}ms) Status: {}", requestId, request.getMethod(), request.getRequestURI(),
                    duration, response.getStatus());
        }
    }
}
