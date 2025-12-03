package org.tradinggate.backend.trading.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * [A-1] Trading API - 보안 설정
 *
 * 역할:
 * - Spring Security 설정
 * - JWT 인증 (선택)
 *
 * TODO:
 * [ ] 인증 필터 설정:
 *     - JWT 토큰 파싱
 *     - userId 추출
 *     - SecurityContext에 저장
 *
 * [ ] API 엔드포인트별 권한 설정:
 *     - /api/orders/** : AUTHENTICATED
 *     - /api/trades/** : AUTHENTICATED
 *
 * [ ] CORS 설정 (필요 시)
 *
 * [ ] @Profile("api")
 */
@Configuration
@EnableWebSecurity
@Profile("api")
public class SecurityConfig {

  // TODO: SecurityFilterChain 정의

  // TODO: JWT 필터 추가 (선택)
}
