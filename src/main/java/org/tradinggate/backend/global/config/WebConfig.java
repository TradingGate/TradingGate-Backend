package org.tradinggate.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * [A-1] Trading API - Web 설정
 *
 * 역할:
 * - CORS, Interceptor 등
 *
 * TODO:
 * [ ] CORS 설정 (필요 시):
 * - allowedOrigins
 * - allowedMethods
 * - allowedHeaders
 *
 * [ ] Request/Response 로깅 Interceptor
 *
 * [ ] @Profile("api")
 */
@Configuration
@Profile("api")
public class WebConfig implements WebMvcConfigurer {

  // TODO: CORS 설정

  // TODO: Interceptor 추가
}
