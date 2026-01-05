package org.tradinggate.backend.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.tradinggate.backend.global.interceptor.RequestLoggingInterceptor;

/**
 * [A-1] Trading API - Web 설정
 *
 * 역할:
 * - CORS, Interceptor 등
 *
 * 구현사항:
 * - [x] CORS 설정: 모든 경로(/**), 모든 출처 허용 (개발 편의성)
 * - [x] Request/Response 로깅 Interceptor 등록
 * - [x] @Profile("api")
 */
@Configuration
@Profile("api")
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

  private final RequestLoggingInterceptor requestLoggingInterceptor;

  @Override
  public void addCorsMappings(@NonNull CorsRegistry registry) {
    registry.addMapping("/**")
        .allowedOriginPatterns("*") // 보안상 필요 시 구체적인 도메인으로 변경
        .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }

  @Override
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    registry.addInterceptor(requestLoggingInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns("/css/**", "/images/**", "/js/**", "/favicon.ico", "/error");
  }
}
