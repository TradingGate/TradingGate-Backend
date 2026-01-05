package org.tradinggate.backend.global.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestLoggingInterceptorTest {

    @InjectMocks
    private RequestLoggingInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Object handler;

    @Test
    @DisplayName("preHandle: requestId와 startTime을 속성에 저장하고 true를 반환한다")
    void preHandle() {
        // given
        // 로그 출력을 위해 필요한 Mock 설정
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        // when
        boolean result = interceptor.preHandle(request, response, handler);

        // then
        assertThat(result).isTrue();
        // requestId와 startTime attribute가 설정되었는지 검증
        verify(request).setAttribute(eq("requestId"), any(String.class));
        verify(request).setAttribute(eq("startTime"), any(Long.class));
    }

    @Test
    @DisplayName("afterCompletion: 예외가 없을 때 정상적으로 실행된다")
    void afterCompletion_success() {
        // given
        when(request.getAttribute("requestId")).thenReturn("test-uuid");
        when(request.getAttribute("startTime")).thenReturn(System.currentTimeMillis());
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(response.getStatus()).thenReturn(200);

        // when
        interceptor.afterCompletion(request, response, handler, null);

        // then
        // Mock 호출 검증
        verify(request).getAttribute("requestId");
        verify(request).getAttribute("startTime");
        verify(response).getStatus();
    }

    @Test
    @DisplayName("afterCompletion: 예외 발생 시에도 정상적으로 실행된다 (Error 로그)")
    void afterCompletion_error() {
        // given
        when(request.getAttribute("requestId")).thenReturn("test-uuid");
        when(request.getAttribute("startTime")).thenReturn(System.currentTimeMillis());
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");

        Exception ex = new RuntimeException("Test Exception");

        // when
        interceptor.afterCompletion(request, response, handler, ex);

        // then
        verify(request).getAttribute("requestId");
        verify(request).getAttribute("startTime");
        // 예외 상황에서는 status를 체크하지 않을 수 있음 (로직에 따라 다름)
    }
}
